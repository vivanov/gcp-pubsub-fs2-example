package org.vilvaadn.pubsubexample

import java.io.File

import java.util.concurrent.Executors

import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import cats.data.{ NonEmptyList, OptionT }
import cats.syntax.option._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.effect.{ ConcurrentEffect, IO, Timer, ContextShift, Sync, Resource, ExitCode }
import cats.effect.implicits._
import io.circe.Json
import fs2._
import fs2.concurrent.Queue

import org.http4s.implicits._
import org.http4s.{ HttpRoutes, StaticFile }
import org.http4s.circe._
import org.http4s.CacheDirective._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s.twirl._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._

import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.catseffect._
import pureconfig.error.ConfigReaderException

import PubSubOps.{ PubSubConfig, subscribe }

object PubSubExampleApp {
  def apply[F[_]: ConcurrentEffect: Timer: ContextShift](config: PubSubConfig, logger: Logger[F]): PubSubExampleApp[F] = new PubSubExampleApp[F](config, logger)
}

class PubSubExampleApp[F[_]](val config: PubSubConfig)(implicit F: ConcurrentEffect[F], timer: Timer[F], cs: ContextShift[F]) extends Http4sDsl[F] {
  def getResource(pathInfo: String) = F.delay(getClass.getResource(pathInfo))

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case request @ GET -> Root / "index.html" =>
      val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      StaticFile.fromResource("/index.html", ec, request.some)
        .getOrElseF(NotFound()) // In case the file doesn't exist
    case GET -> Root / "twirl" =>
      // Supports Play Framework template -- see src/main/twirl.
      Ok(html.index())
    case GET -> Root / "hello" / name =>
      Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
    case GET -> Root / "pubsub" =>
        lazy val error = F.raiseError[String](new Exception("Unable to read subscription name from configuration"))
      val queue = Queue.unbounded[F, String]
      for {
        subscriptionId <- config.subscriptionId.fold(error)(_.pure[F])
        q <- queue
        effect = subscribe[F](q, config.projectId, subscriptionId)(logger).compile.drain
        syncIO = F.runAsync(effect)(_ => IO.unit)
        _ = syncIO.unsafeRunSync
        response <- Ok("Done")
      } yield response

    case GET -> Root / "ws" =>
      lazy val error = F.raiseError[String](new Exception("Unable to read subscription name from configuration"))
      val queue = Queue.unbounded[F, String]
      for {
        subscriptionId <- config.subscriptionId.fold(error)(_.pure[F])
        q <- queue
        fromClient = echoClient(q)
        toClient = subscribe(q, config.projectId, subscriptionId).map(msg => Text(msg))
        built <- WebSocketBuilder[F].build(toClient, fromClient)
      } yield built

    case req if supportedStaticExtensions.exists(req.pathInfo.endsWith) =>
      val ec = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
      StaticFile.fromResource[F](req.pathInfo, ec, req.some)
        .orElse(OptionT.liftF(getResource(req.pathInfo)).flatMap(StaticFile.fromURL[F](_, ec, req.some)))
        .map(_.putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`()))))
        .fold(NotFound())(_.pure[F])
        .flatten
  }

  def blockingThreadPool[F[_]](implicit F: Sync[F]): Resource[F, ExecutionContext] =
    Resource(F.delay {
      val executor = Executors.newCachedThreadPool()
      val ec = ExecutionContext.fromExecutor(executor)
      (ec, F.delay(executor.shutdown()))
    })


  def stream: Stream[F, ExitCode] = for {
    config <- Stream.eval(loadConfigF[F, PubSubConfig])
    built <- BlazeServerBuilder[F]
      .bindHttp(8080)
      .withHttpApp(routes.orNotFound)
      .withWebSockets(true)
      .withIdleTimeout(Duration.Inf)
      .serve
  } yield built

  def echoClient(queue: Queue[F, String])(implicit F: ConcurrentEffect[F]): Sink[F, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) =>
    ws match {
      case Text(t, _) => 
        val msg = s"Client have sent -> $t"
        F.delay(println(msg)) >> queue.enqueue1(msg)
      case other => 
        F.delay(println(s"Unknown type: $other")) >> queue.enqueue1("Client have sent something new")
    }
  }
}
