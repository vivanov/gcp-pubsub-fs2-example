package org.vilvaadn.pubsubexample

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

import cats.effect.{Effect, IO}
import fs2.{ StreamApp, Scheduler, Stream }
import org.http4s.server.blaze.BlazeBuilder

import pureconfig._
import pureconfig.module.catseffect._
import pureconfig.error.ConfigReaderException

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import PubSubOps.PubSubConfig

object PubSubExampleServer extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stream(args: List[String], requestShutdown: IO[Unit]) = ServerStream.stream[IO]
}

object ServerStream {

  def pubSubExampleService[F[_]: Effect](scheduler: Scheduler, config: PubSubConfig, logger: Logger[F]) = new PubSubExampleService[F].service(scheduler, config, logger)

  def stream[F[_]: Effect](implicit ec: ExecutionContext) = for {
    scheduler <- Scheduler[F](corePoolSize = 2)
    logger <- Stream.eval(Slf4jLogger.create[F])
    config <- Stream.eval(loadConfigF[F, PubSubConfig])
    exitCode <- BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(pubSubExampleService(scheduler, config, logger), "/")
      .withWebSockets(true)
      .withIdleTimeout(Duration.Inf)
      .serve
  } yield exitCode
}
