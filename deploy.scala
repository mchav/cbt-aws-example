import java.io._
import java.io.IOException
import java.util.ArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.GetObjectRequest

object Deploy extends App {
  // set CBT home here : should be the directory of the cbt version that supports AWS
  val CBT_HOME   = "/home/chav/Code/Scala/cbt"
  val cloudCBT   = System.getProperty("user.dir") + "/cbt"
  val CLOUD_CBT  = Array[String]("build", "compatibility", "nailgun_launcher", "realpath", "stage1", "stage2")
  val JAVA_SRC   = Array[String]("compatibility", "nailgun_launcher")

  def upload(bucket: String, file: File): Boolean = {
    val s3client = new AmazonS3Client(new ProfileCredentialsProvider())

    try {
      println(s"Uploading ${file.getName} to ${bucket}")
      s3client.putObject(new PutObjectRequest(
                           bucket, file.getName, file))
      true
     } catch {
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
      case ace: AmazonClientException => {
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

  def generateFileList(file: File): List[String] = file match {
    case f if f.isDirectory => f.listFiles.map( sub => generateFileList(sub) ).toList.flatten
    case _                  => List(file.toString.drop(cloudCBT.length + 1) )
  }

  def zip(zipFile: String, zipList: List[String] ): Unit = {
    val buffer = new Array[Byte](1024)
    val source = cloudCBT drop (cloudCBT lastIndexOf "/")
    val fos = new FileOutputStream(zipFile)
    val zos = new ZipOutputStream(fos)
    println("Zipping to: " + zipFile)
    println("From source: " + source)
    zipList.map(file => {
      val ze = new ZipEntry(file)
      zos.putNextEntry(ze)
      try {
        println( "Curently zipping: " + (cloudCBT + "/" + file) )
        val in = new FileInputStream(cloudCBT + "/" + file)
        var len = in.read(buffer)
        while (len > 0) {
          zos.write(buffer, 0, len)
          len = in.read(buffer)
        }
        in.close()
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    })
    zos.closeEntry()
    zos.close()
  }

  def copy(source: File, destination: File, copyTarget: Boolean): Unit = source match {
    case src if src.isDirectory => {
        if ( (source.getName != "target") || copyTarget) {
          destination.mkdir()
          source.listFiles.map(file => {
            copy( file, new File(destination.toString + "/" + file.getName), copyTarget ) 
          })
        }
      }
    case _ => {
        // using streams because lambda doesn't support nio file conversions
        val inStream = new FileInputStream(source)
        val outStream = new FileOutputStream(destination)
        val buffer = new Array[Byte](1024)
      
        var length = inStream.read(buffer)

        //copy the file content in bytes 
        while (length > 0){
          outStream.write(buffer, 0, length)
          length = inStream.read(buffer)
        } 
        inStream.close();
        outStream.close();
      }
  }  

  /* copy the following files WITHOUT TARGET FOLDER into deployment cbt file
   * build, realpath, stage1, stage2
   */

  val cbtHome  = new File(CBT_HOME)
  val cbtCloudDir = new File(cloudCBT)
  if (!cbtCloudDir.exists) {
    cbtCloudDir.mkdir()
  }
  cbtHome.listFiles.filter(CLOUD_CBT contains _.getName).map(src => {
      copy(src, new File(cloudCBT + "/" + src.getName ), JAVA_SRC contains src.getName )
    })
  val cbtZip = new File( System.getProperty("user.dir") + "/cbt.zip")
  if (!cbtZip.exists) {
    val zipList = generateFileList(new File(cloudCBT) )
    zip("cbt.zip", zipList)
  }
  upload("test-bucket-chav", cbtZip)
  
}