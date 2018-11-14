package org.vilvaadn.pubsubexample

import cats.syntax.all._
import cats.effect.{Effect, IO, IOApp, ExitCode}
import cats.effect.implicits._
import fs2._
import org.http4s.server.blaze.BlazeServerBuilder

import pureconfig._
import pureconfig.module.catseffect._
import pureconfig.error.ConfigReaderException

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import PubSubOps.PubSubConfig

object PubSubExampleServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = PubSubExampleApp[IO].stream.compile.drain.as(ExitCode.Success)
}
