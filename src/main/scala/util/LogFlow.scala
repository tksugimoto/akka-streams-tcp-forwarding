package util

import akka.NotUsed
import akka.event.Logging
import akka.stream.Attributes
import akka.stream.scaladsl.Flow
import akka.util.ByteString

object LogFlow {
  def log(name: String): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString]
      .log(name, bs => s"\n${bs.utf8String}")
      .withAttributes(
        Attributes.logLevels(
          onElement = Logging.WarningLevel,
          onFinish = Logging.WarningLevel,
          onFailure = Logging.WarningLevel
        )
      )
}
