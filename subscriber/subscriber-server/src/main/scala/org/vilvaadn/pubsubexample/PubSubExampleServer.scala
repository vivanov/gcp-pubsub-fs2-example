package org.vilvaadn.pubsubexample

import cats.effect.{Effect, IO}
import fs2.{ StreamApp, Scheduler }
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object PubSubExampleServer extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stream(args: List[String], requestShutdown: IO[Unit]) = ServerStream.stream[IO]
}

object ServerStream {

  def pubSubExampleService[F[_]: Effect](scheduler: Scheduler) = new PubSubExampleService[F].service(scheduler)

  def stream[F[_]: Effect](implicit ec: ExecutionContext) = for {
    scheduler <- Scheduler[F](corePoolSize = 2)
    exitCode <- BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(pubSubExampleService(scheduler), "/")
      .withWebSockets(true)
      .serve
  } yield exitCode
}
