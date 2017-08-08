package example.upload.outoforder

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import aws.s3.S3ClientStream
import aws.Configuration.aws
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random


/**
 * demonstrate ability to generate a header while streaming data and prepend that header to the upload
 *
 */
object RunOutOfOrderUploads extends App with LazyLogging {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  def SourceText = {
    """\START-OF-DATA/""" +: Random.alphanumeric.take(aws.s3.chunksize + ModifiedStream.Header.length).grouped(40).map(_.mkString).toSeq
  }

  val bucket = "out-of-order"
  val fname = UUID.randomUUID.toString.take(7)

  //  SourceText.take(10).foreach(println)

  ModifiedStream().multipartUpload(fname, bucket) {
    Source.fromIterator[String](() ⇒ SourceText.iterator).map(ByteString(_))
  }.onComplete { r ⇒
    println(r)
    system.terminate
  }
}

object ModifiedStream {
  val Header = "---insert-header-here---"

  def apply() = new ModifiedStream(S3ClientStream.configureClient)
}

class ModifiedStream(s3Client: AmazonS3) {
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
    .map {
      case p if p.getPartNumber > 1 ⇒
        Right(s3Client.uploadPart(p).getPartETag)
      case p ⇒
        Left(p)
    }
    .runWith(Sink.seq)
    .flatMap { in ⇒
      in.find(_.isLeft) match {
        case Some(Left(h)) ⇒
          val etags = in.filter(_.isRight).map(_.right.get)

          StreamConverters.fromInputStream(h.getInputStream, aws.s3.chunksize)
          .via(S3ClientStream.rechunk(aws.s3.chunksize))
          .map { s ⇒
            val bs = ByteString(ModifiedStream.Header) ++ s
            h.withInputStream(bs.iterator.asInputStream).withPartSize(bs.length)
          }.runWith(Sink.head).map { h ⇒
            val headtag = s3Client.uploadPart(h).getPartETag
            val alltags = headtag +: etags
            s3Client.completeMultipartUpload(
              new CompleteMultipartUploadRequest(bucket, key, initUpload.getUploadId, alltags.asJava)
            )
          }


        case _ ⇒
          s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, initUpload.getUploadId))
          Future.failed(new RuntimeException("couldnt find header; aborting upload"))
      }
    }
  }
}
