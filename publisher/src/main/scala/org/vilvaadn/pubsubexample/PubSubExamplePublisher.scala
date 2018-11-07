package org.vilvaadn.pubsubexample

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.pubsub.v1.ProjectTopicName
import com.google.cloud.pubsub.v1.Publisher
import com.google.api.gax.rpc.ApiException
import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage


import io.grpc.{ ManagedChannelBuilder, ManagedChannel }
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{ TransportChannelProvider, FixedTransportChannelProvider }
import com.google.api.gax.core.{ CredentialsProvider, NoCredentialsProvider }

import scala.concurrent.ExecutionContext
import cats.syntax.traverse._
import cats.syntax.either._
import cats.instances.list._
import cats.effect.{ Async, Effect, IO }
import fs2._


object PubSubExamplePublisher {

  def publishMessage(publisher: Publisher)(message: String): ApiFuture[String] = {
    println(s"Publishing message: $message")
    val data = ByteString.copyFromUtf8(message)
    val pubsubMessage = PubsubMessage.newBuilder().setData(data).build()
    publisher.publish(pubsubMessage)
  }

  def publishMessages(publisher: Publisher)(messages: List[String]): List[ApiFuture[String]] =
    messages.map(message => publishMessage(publisher)(message))

  def apiFutureCallback[F[_]](future: ApiFuture[String])(implicit F: Effect[F], ec: ExecutionContext) = Async[F].async[String] { (cb: Either[Throwable, String] => Unit) => ApiFutures.addCallback(future, new ApiFutureCallback[String]() {
    override def onFailure(error: Throwable) = {
      error match {
        case apiException: ApiException =>
          println(s"ApiException was thrown while publishing message, code: ${apiException.getStatusCode().getCode()}, retryable: ${apiException.isRetryable()}, message: ${apiException.getMessage}")
        case _ =>
          println(s"Error publishing message: ${error.getMessage()}")
      }
      cb(error.asLeft[String])
    }

    override def onSuccess(messageId: String) = {
      println(s"Message sucessfully published, message Id: $messageId")
      cb(messageId.asRight[Throwable])
    }
  })}

  def publish[F[_]](implicit F: Effect[F], ec: ExecutionContext): Stream[F, List[String]] = {
    val projectId = "pubs-tst"
    val topicId = "my-tst-topic"
    val topicName = ProjectTopicName.of(projectId, topicId)
    val channel = ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext(true).build()
    val messageTemplate = "pubsubexample"

    for {
      channelProvider <- Stream.eval(F.delay(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))))
      credentialsProvider <- Stream.eval(F.delay(NoCredentialsProvider.create()))
      nums = Stream.emit(1).repeat.scan1(_ + _).take(10).toList
      messages = nums.map(num => s"$messageTemplate$num")
      futures <- Stream.bracket(F.delay(Publisher.newBuilder(topicName).setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build()))(publisher => Stream.eval(F.delay(publishMessages(publisher)(messages))), publisher => F.delay(publisher.shutdown()))
      messageIds <- Stream.eval(futures.traverse(future => apiFutureCallback(future)(F, ec)))
      _ <- Stream.eval_(F.delay(channel.shutdown()))
    } yield messageIds
  }

  def main(args: Array[String]): Unit = {
    import ExecutionContext.Implicits.global
    val io = publish[IO]
    io.compile.drain.unsafeRunSync
  }
}
