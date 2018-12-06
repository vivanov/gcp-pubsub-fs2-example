package org.vilvaadn.pubsubexample

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import cats.syntax.all._
import cats.instances.list._
import cats.effect.{ IO, ExitCode, IOApp }

import com.typesafe.config.ConfigFactory 

import fs2._
import com.typesafe.config.ConfigFactory
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import PubSubOps.{ PubSubConfig, publish }

// For now Publisher just generates 10 test messages
// and sends it to the topic every 10 second
object PubSubExamplePublisher extends IOApp {
  def generateMessages: List[String] = {
    val messageTemplate = "pubsubexample"
    val nums = Stream.emit(1).repeat.scan1(_ + _).take(10).toList
    nums.map(num => s"$messageTemplate$num")
  }

  def run(args: List[String]): IO[ExitCode] = {
    val messages = generateMessages
    lazy val error = IO.raiseError[String](new Exception("Unable to read topic name from configuration"))

    
    val stream = for {
      logger <- Stream.eval(Slf4jLogger.create[IO])
      _ <- Stream.eval(logger.info(s"Getting configuration"))
      config <- Stream.eval(loadConfigF[IO, PubSubConfig](ConfigFactory.load(getClass.getClassLoader)))
      topicId <- Stream.eval(config.topicId.fold(error)(_.pure[IO]))
      _ <- Stream.eval(logger.info(s"Publishing messages"))
      published = publish[IO](config.projectId, topicId, messages)(logger)
      _ <- published
    } yield ()
    val scheduled = Stream.awakeEvery[IO](10.second).flatMap(_ => stream)
    val io = scheduled.compile.drain
    io.as(ExitCode.Success)
  }
}
