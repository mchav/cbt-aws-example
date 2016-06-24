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
  val bucketName     = "test-bucket-chav"
  val keyName        = "chav"
  val uploadFileName = "/home/chav/Code/Scala/Lambda-Example/cbt/cbt.zip"
  
  val s3client = new AmazonS3Client(new ProfileCredentialsProvider())
  println("Working Directory = " + System.getProperty("user.dir"))
  try {
    println("Uploading a new object to S3 from a file\n")
    val file = new File(uploadFileName)
    val isOk = s3client.putObject(new PutObjectRequest(
                         bucketName, "code", file))
    
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
      }
    case ace: AmazonClientException => {
        println("Caught an AmazonClientException, which " +
                "means the client encountered " +
                "an internal error while trying to " +
                "communicate with S3, " +
                "such as not being able to access the network.")
        println("Error Message: " + ace.getMessage)
      }
  }
}