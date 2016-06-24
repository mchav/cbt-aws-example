import cbt._
import java.net.URL
import java.nio._
import java.nio.file.Files._
import java.io.File
import scala.collection.immutable.Seq
import java.util.jar._
import java.util.Enumeration

class Build( context: Context ) extends BasicBuild( context ) with PackageJars {
  override def name = "ObjectExample"
  override def defaultVersion = "1.0.0"
  override def groupId = ""
  override def runClass = "UploadFile"
  override def dependencies = (
    super.dependencies // don't forget super.dependencies here
    ++
    Resolver( mavenCentral ).bind(
      MavenDependency( "com.amazonaws", "aws-java-sdk", "1.11.8" ),
      MavenDependency( "com.amazonaws", "aws-lambda-java-core", "1.1.0"),
      MavenDependency( "com.amazonaws", "aws-java-sdk-s3", "1.11.9"),
      MavenDependency( "com.amazonaws", "aws-java-sdk-events", "1.11.9" ),
      MavenDependency( "com.amazonaws", "aws-lambda-java-events", "1.3.0")
    )
  )
  def extractJar(jarFile: File, temp: File): Unit = {
    val jar = new java.util.jar.JarFile(jarFile);
    val enumEntries = jar.entries();
    
    while (enumEntries.hasMoreElements()) {
      val file = enumEntries.nextElement().asInstanceOf[JarEntry]
      println("Unpacking: " + file.toString)
      
      val f = new java.io.File("/home/chav/Code/Scala/fatJar/temp/" + java.io.File.separator + file.getName());
      
      if (file.isDirectory()) { 
          f.mkdir();
      } else {
        try {
          val is = jar.getInputStream(file); // get the input stream
          val fos = new java.io.FileOutputStream(f);
          while (is.available() > 0) {  // write contents of 'is' to 'fos'
              fos.write(is.read());
          }

          fos.close();
          is.close();
          } catch {
            case e : Throwable => println("Failed to unpack " + file.toString)
          }
      }
    }
  }

  def fatJar(): Unit = {
    val comp = compileTarget
    val jarT = jarTarget
    val classes = dependencyClasspath match {
      case ClassPath(xs) => xs
      case _             => List()
    }
    val temp = new File ("/home/chav/Code/Scala/fatJar/temp/")
    
    temp.mkdir()
    classes.map(x => extractJar(x, temp))
    lib.jarFile(new File("./demo.jar"), Seq(temp, comp))
  }
}
