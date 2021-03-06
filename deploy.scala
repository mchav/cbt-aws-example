import java.io._
import java.nio.ByteBuffer
import java.nio.channels._
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import scala.sys.process._

import com.google.common.collect.ImmutableList
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs


import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda._
import com.amazonaws.services.lambda.model._
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

object Deploy {
  val TARGET = "/target/scala-2.11/classes/"
  val fs = Jimfs.newFileSystem(Configuration.unix())
  val data = fs.getPath("/data")
  Files.createDirectory(data)

  val bucketName = "sample-game"
  
  def main(args: Array[String]): Unit = {
    deployWithCbt(bucketName, new File("projects"))
  }

  // plugin that implements this plugin should override these parameters
  // fine here for now
  def cloudCompile(project: File
                  , bucketName: String) {
    val projectName = project.getName
    val s3client = new AmazonS3Client(new ProfileCredentialsProvider())
    val lambdaClient = new AWSLambdaClient(new ProfileCredentialsProvider())
    val functionName = projectName ++ "_function"
    val arn: String = try {
      val url = s3client.getResourceUrl(bucketName, s"${projectName}_files.zip")
      val codeLocation = new FunctionCodeLocation().withLocation(url)
      val getFunctionRequest = new GetFunctionRequest().withFunctionName(functionName)
      val getFunctionResult = lambdaClient getFunction getFunctionRequest
      getFunctionResult.setCode(codeLocation)
      getFunctionResult.getConfiguration.getFunctionArn
    } catch {
      case e: ResourceNotFoundException => {
        val createResult = createLambda(functionName, "RunCbt::cbtHandler", s"${projectName}_files.zip"
                                     , s"CBT compilation lambda for ${projectName}"
                                     , "arn:aws:iam::753346471543:role/lambda_basic_execution", 1536, 300)
        createResult.getFunctionArn
      }
      case _: Throwable => throw new IllegalArgumentException("Failed to get or read function from bucket")
    }
    val req = new InvokeRequest().withFunctionName(arn)
    val buffer = lambdaClient.invoke(req).getPayload
    val stringResult = new String(buffer.array)
    val target = new File(project.toString + TARGET)
    val buildTarget = new File(project.toString + "/build" + TARGET)
    target.mkdirs
    buildTarget.mkdirs
    println(s"Downloading compiled files to ${target.toString} and ${buildTarget.toString}")
    val targetIS = s3client.getObject(bucketName, "target.zip").getObjectContent
    val buildIS  = s3client.getObject(bucketName, "buildTarget.zip").getObjectContent
    unzip(targetIS, target.toString)
    unzip(buildIS, buildTarget.toString)

    // make compile setup consistent
    val targetCache = new File(target.getParentFile.toString + "/cache")
    val buildTargetCache = new File(buildTarget.getParentFile.toString + "/cache")
    targetCache.mkdirs
    buildTargetCache.mkdirs
    Files.copy(new File(target.toString + "/classes.last-success").toPath
              , new File(target.getParentFile.toString + "/classes.last-success").toPath
              , StandardCopyOption.REPLACE_EXISTING )
    Files.copy(new File(buildTarget.toString + "/classes").toPath
              , new File(buildTarget.getParentFile.toString + "/classes.last-success").toPath
              , StandardCopyOption.REPLACE_EXISTING )
    Files.copy(new File(target.toString + "/classes").toPath
              , new File(targetCache.toString + "/classes").toPath
              , StandardCopyOption.REPLACE_EXISTING )
    Files.copy(new File(buildTarget.toString + "/classes").toPath
              , new File(buildTargetCache.toString + "/classes").toPath
              , StandardCopyOption.REPLACE_EXISTING )
    println(s"Compiled files for ${projectName}")
  }

  def unzip(zipFile: S3ObjectInputStream, outputFolder: String): Unit = {
    val buffer = new Array[Byte](1024)
    try {
      //zip file content
      val zis: ZipInputStream = new ZipInputStream(zipFile)
      //get the zipped file list entry
      var ze: ZipEntry = zis.getNextEntry()
      while (ze != null) {
        val fileName = ze.getName()
        //if (fileName == "classes")
        val newFile = new File(outputFolder + File.separator + fileName)
        //create folders
        new File(newFile.getParent()).mkdirs()

        val fos = new FileOutputStream(newFile)
        var len: Int = zis.read(buffer);
        while (len > 0) {
          fos.write(buffer, 0, len)
          len = zis.read(buffer)
        }
        fos.close()
        ze = zis.getNextEntry()
      }

      zis.closeEntry()
      zis.close()

    } catch {
      case e: IOException => println("exception caught: " + e.getMessage)
    }

  }

  def createLambda( name: String
                  , handler: String
                  , s3Key: String
                  , description: String
                  , role: String
                  , memory: Int
                  , timeout: Int): CreateFunctionResult = {

    val client = new AWSLambdaClient(new ProfileCredentialsProvider())
    val lambdaBuilder = new CreateFunctionRequest()
    val functionCodeBuilder = new FunctionCode()
    
    val functionCode = functionCodeBuilder.withS3Bucket(bucketName).withS3Key(s3Key)
    val lambda = lambdaBuilder.withRuntime(Runtime.Java8)
      .withRole(role)
      .withFunctionName(name)
      .withHandler(handler)
      .withMemorySize(memory)
      .withTimeout(timeout)
      .withCode(functionCode)
    client.createFunction(lambda)
  }

  def getDependencies(file: File) = {
    val contents = Process("cbt dependencyClasspath", file).lineStream.toList.map( _.split(":")).flatten//.map(file => new File(file
    contents.map{dep => 
      val versions = dep.take(dep.lastIndexOf('/'))
      versions.take(versions.lastIndexOf('/'))
    }
  }

  def deployWithCbt(bucket: String, project: File) = {
    val CBT_HOME   = System.getenv("CBT_HOME")
    val cacheFiles  = new File(CBT_HOME + "/cache")
    val cbtHome    = new File(CBT_HOME)
    val cbtDeps    = getDependencies(cbtHome) ++ getDependencies(new File("."))
    val cloudCBT   = data.resolve("cbt")
    val MIN_CBT    = Array[String]("build", "compatibility", "nailgun_launcher", "realpath", "stage1", "stage2")
    val JAVA_SRC   = Array[String]("compatibility", "nailgun_launcher")
    val target     = new File(System.getProperty("user.dir") + "/target/scala-2.11/classes/" ) 
    val sourceFiles = target.listFiles.toList ++ cbtHome.listFiles.toList.filter(MIN_CBT contains _.getName)
    val cbtZipList = sourceFiles.map(src => {
      val copyTargetFolder = JAVA_SRC contains src.getName
      copy(src.toPath, data.resolve(src.getName), true, None )
    }).flatten.map(_.toString).toList

    val cache = fs.getPath("/data/cache")
    Files.createDirectory(cache)
    val cacheZipList = cacheFiles.listFiles.toList.map(src => {
      copy(src.toPath, cache.resolve(src.getName), true, None)
    }).flatten.map(_.toString).toList

    val userCode = fs.getPath("/data/code")
    Files.createDirectory(userCode)
    val codeZipList = project.listFiles.toList.map(src => {
      copy(src.toPath, userCode.resolve(src.getName), false, None)
    }).flatten.map(_.toString).toList

    val projZipList = cbtZipList ++ codeZipList ++ cacheZipList
    zip(s"${project.getName}_files.zip", data, projZipList)
    upload(bucket, new File(s"${project.getName}_files.zip") )
    cloudCompile(project, bucket)
  }

  def deploy(bucket: String, source: File) = {
    val zipFile = new File( s"/tmp/${source.getName}.zip")
    val zipList = source.listFiles.toList.map(src => {
      copy(src.toPath, data.resolve(src.getName), true, None)
    }).flatten.map(_.toString).toList
    zip(zipFile.toString, source.toPath, zipList)
    upload(bucket, zipFile)
  }

  def listAll(file: File): List[String] = file match {
    case file if file.isDirectory => file.listFiles.toList.map(listAll _).flatten
    case file                     => List(file.toString)
  }
  
  def createBucket(bucketName: String, region: Regions) = {
    val s3client = new AmazonS3Client(new ProfileCredentialsProvider())
    s3client.setRegion(Region.getRegion(region))
    try {
      if(!(s3client.doesBucketExist(bucketName))) {
        s3client.createBucket(new CreateBucketRequest(bucketName))
      }
    } catch {
      case e: AmazonClientException => handleException(e)
    }
  }

  def upload(bucket: String, file: File): Boolean = {
    val s3client = new AmazonS3Client(new ProfileCredentialsProvider())
    try {
      println(s"Uploading ${file.getName} to ${bucket}")
      s3client.putObject(new PutObjectRequest(
                           bucket, file.getName, file))
      true
     } catch {
        case e: AmazonClientException => { 
          handleException(e)
          false
        }
    } 
  }

  def zip(zipFile: String, sourceFile: Path, zipList: List[String] ): Unit = {
    val fos = new FileOutputStream(zipFile)
    val zos = new ZipOutputStream(fos)
    println("Zipping to: " + zipFile)
    zipList.map(file => {
      val ze = new ZipEntry(file.drop("/data/".length))
      zos.putNextEntry(ze)
      try {
        //println( "Curently zipping: " + file )
        val fileData = Files.readAllBytes(fs.getPath(file))
        zos.write(fileData, 0, fileData.length)
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    })
    zos.closeEntry()
    zos.close()
  }

  def copy(source: Path, destination: Path, copyTarget: Boolean, include: Option[List[String]]): List[Path] = source match {
    case src if src.toFile.isDirectory => {
        val files = if ( ((source.toFile.getName != "target") || copyTarget) && !source.toFile.getName.startsWith(".")) {
                      Files createDirectory destination
                      source.toFile.listFiles.toList.map(file => {
                        copy( file.toPath, destination.resolve(file.getName) , copyTarget, include ) 
                      }).flatten
                    }
                    else List[Path]()
        files
      }
    case _ => { include match {
        case None => { 
          Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING )
          List(destination)
        }
        case Some(f) => { 
          val fileName = source.toFile.toString
          val versions = fileName.take(fileName.lastIndexOf('/'))
          if (f contains versions.take(versions.lastIndexOf('/'))) {  
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING )
            List(destination)
          } else List[Path]()
        }
      }
    }
  }  

  def handleException(e: AmazonClientException) = e match {
    case ase: AmazonServiceException => {
        println("Caught an AmazonServiceException, which " +
                "means your request made it " +
                "to Amazon S3, but was rejected with an error response" +
                " for some reason.");
        println("Error Message:    " + ase.getMessage)
        println("HTTP Status Code: " + ase.getStatusCode)
        println("AWS Error Code:   " + ase.getErrorCode)
        println("Error Type:       " + ase.getErrorType)
        println("Request ID:       " + ase.getRequestId)
        false
      }
    case ace => {
        println("Caught an AmazonClientException, which " +
                "means the client encountered " +
                "an internal error while trying to " +
                "communicate with S3, " +
                "such as not being able to access the network.")
        println("Error Message: " + ace.getMessage)
        false
      }
  }
}