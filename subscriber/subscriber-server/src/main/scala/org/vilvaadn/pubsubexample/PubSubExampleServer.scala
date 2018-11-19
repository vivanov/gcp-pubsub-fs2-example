package org.vilvaadn.pubsubexample

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import com.typesafe.config.ConfigFactory 

import cats.syntax.all._
import cats.effect.{Effect, IO, IOApp, ExitCode}
import cats.effect.implicits._
import fs2._
import org.http4s.server.blaze.BlazeServerBuilder

import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import pureconfig.error.ConfigReaderException

import PubSubOps.PubSubConfig

object PubSubExampleServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val io = for {
      logger <- Slf4jLogger.create[IO]
      _ <- logger.info(s"Getting configuration")
      config <- loadConfigF[IO, PubSubConfig](ConfigFactory.load(getClass.getClassLoader))
      _ <- logger.info(s"Running service")
      result <- PubSubExampleApp[IO](config, logger).stream.compile.drain
    } yield result
    io.as(ExitCode.Success)
  }
}
