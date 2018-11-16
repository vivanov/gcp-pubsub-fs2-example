package org.vilvaadn.pubsubexample

import scala.concurrent.ExecutionContext
import cats.syntax.applicative._
import cats.syntax.traverse._
import cats.syntax.either._
import cats.instances.list._
import cats.effect.{ Async, Effect, IO }
import fs2._
import pureconfig._
import pureconfig.module.catseffect._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import PubSubOps.{ PubSubConfig, publish }

object PubSubExamplePublisher {
  // For now Publisher just generates 10 test messages to sent to the topic
  def generateMessages: List[String] = {
    val messageTemplate = "pubsubexample"
    val nums = Stream.emit(1).repeat.scan1(_ + _).take(10).toList
    nums.map(num => s"$messageTemplate$num")
  }

  def main(args: Array[String]): Unit = {
    import ExecutionContext.Implicits.global
    val messages = generateMessages
    lazy val error = IO.raiseError[String](new Exception("Unable to read topic name from configuration"))

    
    val io = for {
      logger <- Slf4jLogger.create[IO]
      _ <- logger.info(s"Getting configuration")
      config <- loadConfigF[IO, PubSubConfig]
      topicId <- config.topicId.fold(error)(_.pure[IO])
      _ <- logger.info(s"Publishing messages")
      published = publish[IO](config.projectId, topicId, messages)(logger)
      _ <- published.compile.drain
    } yield ()
    io.unsafeRunSync
  }
}
