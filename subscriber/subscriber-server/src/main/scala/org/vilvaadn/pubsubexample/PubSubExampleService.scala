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

class PubSubExampleService[F[_]](implicit F: Effect[F]) extends Http4sDsl[F] {
  def getResource(pathInfo: String) = F.delay(getClass.getResource(pathInfo))

  val supportedStaticExtensions =
    List(".html", ".js", ".map", ".css", ".png", ".ico")

  def service(scheduler: Scheduler): HttpService[F] = {
    HttpService[F] {
      case request @ GET -> Root / "index.html" =>
        StaticFile.fromResource("/index.html", request.some)
          .getOrElseF(NotFound()) // In case the file doesn't exist
      /*  
      case GET -> Root =>
        // Supports Play Framework template -- see src/main/twirl.
        Ok(html.index())
       */
      case GET -> Root / "hello" / name =>
        Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
      case GET -> Root / "pubsub" =>
        import ExecutionContext.Implicits.global
        messages[IO].compile.drain.unsafeRunSync
        Ok("Done")
      case GET -> Root / "ws" =>
        import ExecutionContext.Implicits.global
        val toClient: Stream[F, WebSocketFrame] =
          //scheduler.awakeEvery[F](1.seconds).map(d => Text(s"Ping! $d"))
          messages[F].map(message => Text(s"Message: $message"))
        val fromClient: Sink[F, WebSocketFrame] = _.evalMap { (ws: WebSocketFrame) =>
          ws match {
            case Text(t, _) => F.delay(println(t))
            case f => F.delay(println(s"Unknown type: $f"))
          }
        }
        WebSocketBuilder[F].build(toClient, fromClient)

      case req if supportedStaticExtensions.exists(req.pathInfo.endsWith) =>
        StaticFile.fromResource[F](req.pathInfo, req.some)
          .orElse(OptionT.liftF(getResource(req.pathInfo)).flatMap(StaticFile.fromURL[F](_, req.some)))
          .map(_.putHeaders(`Cache-Control`(NonEmptyList.of(`no-cache`()))))
          .fold(NotFound())(_.pure[F])
          .flatten
    }
  }

  def messages[F[_]](implicit F: Effect[F], ec: ExecutionContext): Stream[F, String] = {
    val projectId = "pubs-tst"
    val subscriptionId = "my-tst-subscription"
    //val hostport = sys.env("PUBSUB_EMULATOR_HOST")
    val channel = ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext(true).build()
    val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)

    def messageReceiver(queue: Queue[F, String]) = new MessageReceiver {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) =
        F.runAsync(queue.enqueue1(message.getData().toStringUtf8()))(_ => IO(consumer.ack())).unsafeRunSync
    }
    for {
      channelProvider <- Stream.eval(F.delay(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))))
      credentialsProvider <- Stream.eval(F.delay(NoCredentialsProvider.create()))
      _ = println(s"Creating queue")
      queue <- Stream.eval(Queue.unbounded[F, String])
      _ = println(s"Creating receiver")
      receiver = messageReceiver(queue)
      _ = println(s"Creating subscriber")
      _ <- Stream.bracket(F.delay(Subscriber.newBuilder(subscriptionName, receiver).setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build()))( subscriber => Stream.eval(F.delay(subscriber.startAsync())), subscriber => F.delay(subscriber.stopAsync()) )
      _ = println(s"Retrieving message from queue")
      message <- queue.dequeue
      //_ <- Stream.eval_(F.delay(println(s"Message received: $message")))
    } yield message
  }
}
