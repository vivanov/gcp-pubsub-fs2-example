package org.vilvaadn.pubsubexample

import java.io.File

import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.PubsubMessage

import io.grpc.{ ManagedChannelBuilder, ManagedChannel }
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{ TransportChannelProvider, FixedTransportChannelProvider }
import com.google.api.gax.core.{ CredentialsProvider, NoCredentialsProvider }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import cats.data.{ NonEmptyList, OptionT }
import cats.syntax.option._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.effect.{ Effect, IO }
import io.circe.Json
import fs2._
import fs2.async.mutable.Queue
import org.http4s.{ HttpService, StaticFile }
import org.http4s.circe._
import org.http4s.CacheDirective._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s.twirl._
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._

import PubSubOps.subscribe

class PubSubExampleService[F[_]](implicit F: Effect[F]) extends Http4sDsl[F] {
  def getResource(pathInfo: String) = F.delay(getClass.getResource(pathInfo))

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  def service(scheduler: Scheduler): HttpService[F] = {
    HttpService[F] {
      case request @ GET -> Root / "index.html" =>
        StaticFile.fromResource("/index.html", request.some)
          .getOrElseF(NotFound()) // In case the file doesn't exist
      case GET -> Root / "twirl" =>
        // Supports Play Framework template -- see src/main/twirl.
        Ok(html.index())
      case GET -> Root / "hello" / name =>
        Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
      case GET -> Root / "pubsub" =>
        import ExecutionContext.Implicits.global
        //TODO: Following settings have to be moved into configuration
        val projectId = "pubs-tst"
        val subscriptionId = "my-tst-subscription"
        val queue = async.unboundedQueue[F, String]
        queue.map { q =>
          val effect = subscribe[F](q, projectId, subscriptionId).compile.drain
          val syncIO = F.runAsync(effect)(_ => IO.unit)
          syncIO.unsafeRunSync
        } >> Ok("Done")
      case GET -> Root / "ws" =>
        import ExecutionContext.Implicits.global
        //TODO: Following settings have to be moved into configuration
        val projectId = "pubs-tst"
        val subscriptionId = "my-tst-subscription"
        val queue = async.unboundedQueue[F, String]
        queue flatMap { q =>
          val fromClient = echoClient(q)
          val toClient = subscribe(q, projectId, subscriptionId).map(msg => Text(msg))
          WebSocketBuilder[F].build(toClient, fromClient)
        }

      case req if supportedStaticExtensions.exists(req.pathInfo.endsWith) =>
        StaticFile.fromResource[F](req.pathInfo, req.some)
          .orElse(OptionT.liftF(getResource(req.pathInfo)).flatMap(StaticFile.fromURL[F](_, req.some)))
          .map(_.putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`()))))
          .fold(NotFound())(_.pure[F])
          .flatten
    }
  }

  def echoClient(queue: Queue[F, String])(implicit F: Effect[F]): Sink[F, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) =>
    ws match {
      case Text(t, _) => 
        val msg = s"Client have sent: $t"
        F.delay(println(msg)) >> queue.enqueue1(msg)
      case other => 
        F.delay(println(s"Unknown type: $other")) >> queue.enqueue1("Client have sent something new")
    }
  }
}
