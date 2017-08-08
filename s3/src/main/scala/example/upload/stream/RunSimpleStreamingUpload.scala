package example.upload.stream

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import aws.s3.S3ClientStream
import com.typesafe.scalalogging.LazyLogging


object RunSimpleStreamingUpload extends App with LazyLogging {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  val SourceText =
    """
      |a
      |b
      |c
    """.stripMargin

  val bucket = "simple"
  val fname = UUID.randomUUID.toString.take(7)

  S3ClientStream().multipartUpload(fname, bucket) {
    Source.fromIterator[String](() ⇒ SourceText.lines).map(ByteString(_))
  }
}
