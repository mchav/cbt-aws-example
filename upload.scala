import java.io.File
import java.io.IOException

import com.amazonaws.AmazonClientException
import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.GetObjectRequest

object UploadFile extends App {
  val bucketName     = "test-for-cbt2"
  val keyName        = "chav"
  val uploadFileName = "/home/chav/Code/Scala/Lambda-Example/temp/temp.zip"
  
  val s3client = new AmazonS3Client(new ProfileCredentialsProvider())
  println("Working Directory = " + System.getProperty("user.dir"))
  try {
    println("Uploading a new object to S3 from a file\n")
    val file = new File(uploadFileName)
    val isOk = s3client.putObject(new PutObjectRequest(
                         bucketName, "code", file))
    
    val ret = s3client.getObject(new GetObjectRequest(
                         bucketName + "resized", "resized-HappyFace.jpg")).getObjectMetadata.getCacheControl
    println(ret)
   } catch {
    case e: AmazonServiceException => println("Error Message:    " + e.getMessage)
  }
}
