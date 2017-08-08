package aws

import com.amazonaws.regions.RegionUtils
import com.typesafe.config.ConfigFactory
import eri.commons.config.SSConfig

object Configuration {

  val values = ConfigFactory.load()
  private val config = new SSConfig(values)

  object aws {
    val accessKey = config.aws.accessKey.asOption[String]
    val secretKey = config.aws.secretKey.asOption[String]

    val region = RegionUtils.getRegion(config.aws.region.as[String])

    object s3 {
      val endpoint = config.aws.s3.endpoint.asOption[String]
      val bucket = config.aws.s3.bucket.as[String]

      val defaultChunksize = 6291456
      val chunksize = config.aws.s3.chunksize.asOption[Int].getOrElse(defaultChunksize)
    }
  }
}
