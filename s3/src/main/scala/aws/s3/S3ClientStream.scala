package aws.s3

import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage._
import akka.util.ByteString
import aws.Configuration._
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.model._
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client, S3ClientOptions}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future}


class S3ClientStream(s3Client: AmazonS3) {
  def multipartUpload(key: String, bucket: String)(source: Source[ByteString, _])(implicit mat: ActorMaterializer, ec: ExecutionContext): Future[CompleteMultipartUploadResult] = {
    val initUpload = s3Client.initiateMultipartUpload(new InitiateMultipartUploadRequest(bucket, key))
    source.via(S3ClientStream.rechunk(aws.s3.chunksize)).statefulMapConcat {
      () ⇒ {
        var idx = 0
        bs ⇒ {
          idx += 1
          List(
            new UploadPartRequest()
            .withBucketName(bucket)
            .withKey(key)
            .withUploadId(initUpload.getUploadId)
            .withPartNumber(idx)
            .withPartSize(bs.length)
            .withInputStream(bs.iterator.asInputStream)
          )
        }
      }
    }
    .map(p ⇒ s3Client.uploadPart(p).getPartETag)
    .runWith(Sink.seq)
    .map { etags ⇒
      s3Client.completeMultipartUpload(
        new CompleteMultipartUploadRequest(bucket, key, initUpload.getUploadId, etags)
      )
    }
  }

}

object S3ClientStream {
  def apply() = new S3ClientStream(configureClient)

  def configureClient = {
    val client = aws.accessKey → aws.secretKey match {
      case (Some(a), Some(s)) ⇒ new AmazonS3Client(
        new AWSStaticCredentialsProvider(
          new BasicAWSCredentials(a, s)
        )
      )
      case _ ⇒ new AmazonS3Client
    }

    client.withRegion(aws.region)
    aws.s3.endpoint.foreach(client.withEndpoint)
    client.setS3ClientOptions(
      S3ClientOptions.builder.setPathStyleAccess(true).build
    )

    client
  }

  /**
   * Rechunk a stream of bytes according to a chunk size.
   *
   * @param chunkSize the new chunk size
   * @return
   */

  def rechunk(chunkSize: Int) = new GraphStage[FlowShape[ByteString, ByteString]] {
    val in = Inlet[ByteString]("S3Chunker.in")
    val out = Outlet[ByteString]("S3Chunker.out")

    override val shape = FlowShape.of(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
      new GraphStageLogic(shape) {
        var buffer = ByteString.empty

        setHandler(in, new InHandler {
          override def onPush(): Unit = {
            buffer ++= grab(in)
            emitOrPull()
          }

          override def onUpstreamFinish() = if (isAvailable(shape.out)) getHandler(out).onPull()
        })


        setHandler(out, new OutHandler {
          override def onPull(): Unit = emitOrPull()
        })


        def emitOrPull() = {
          if (isClosed(in)) {
            if (buffer.isEmpty) completeStage()
            else if (buffer.length < chunkSize) {
              push(out, buffer)
              completeStage()
            }
            else {
              val (emit, nextBuffer) = buffer.splitAt(chunkSize)
              buffer = nextBuffer
              push(out, emit)
            }
          }
          else {
            if (buffer.length < chunkSize) pull(in)
            else {
              val (emit, nextBuffer) = buffer.splitAt(chunkSize)
              buffer = nextBuffer
              push(out, emit)
            }
          }
        }
      }
    }
  }
}
