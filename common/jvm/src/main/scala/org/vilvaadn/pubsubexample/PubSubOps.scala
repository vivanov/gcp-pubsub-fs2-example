package org.vilvaadn.pubsubexample

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.ProjectTopicName

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures

import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber

import com.google.api.gax.rpc.ApiException
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{ TransportChannelProvider, FixedTransportChannelProvider }
import com.google.api.gax.core.{ CredentialsProvider, NoCredentialsProvider }

import io.grpc.{ ManagedChannelBuilder, ManagedChannel }

import scala.concurrent.ExecutionContext

import cats.syntax.traverse._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.instances.list._
import cats.effect.{ Async, Effect, IO }
import fs2._
import fs2.async.mutable.Queue


object PubSubOps {
  private def publishMessage(publisher: Publisher)(message: String): ApiFuture[String] = {
    println(s"Publishing message: $message")
    val data = ByteString.copyFromUtf8(message)
    val pubsubMessage = PubsubMessage.newBuilder().setData(data).build()
    publisher.publish(pubsubMessage)
  }

  private def publishMessages(publisher: Publisher)(messages: List[String]): List[ApiFuture[String]] =
    messages.map(message => publishMessage(publisher)(message))

  private def apiFutureCallback[F[_]](future: ApiFuture[String])(implicit F: Effect[F], ec: ExecutionContext) = Async[F].async[String] { (cb: Either[Throwable, String] => Unit) => ApiFutures.addCallback(future, new ApiFutureCallback[String]() {
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


  def publish[F[_]](projectId: String, topicId: String, messages: List[String])(implicit F: Effect[F], ec: ExecutionContext): Stream[F, List[String]] = {
    val topicName = ProjectTopicName.of(projectId, topicId)
    val channel = ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext(true).build()

    for {
      //Settings Provider and Credentials Provider are only required for PubSub Emulator
      channelProvider <- Stream.eval(F.delay(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))))
      credentialsProvider <- Stream.eval(F.delay(NoCredentialsProvider.create()))
      futures <- Stream.bracket(F.delay(Publisher.newBuilder(topicName).setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build()))(publisher => Stream.eval(F.delay(publishMessages(publisher)(messages))), publisher => F.delay(publisher.shutdown()))
      messageIds <- Stream.eval(futures.traverse(future => apiFutureCallback(future)(F, ec)))
      _ <- Stream.eval_(F.delay(channel.shutdown()))
    } yield messageIds
  }

  def subscribe[F[_]](queue: Queue[F, String], projectId: String, subscriptionId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, String] = {
    //val hostport = sys.env("PUBSUB_EMULATOR_HOST")

    val channel = ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext(true).build()
    val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)

    val receiver = new MessageReceiver {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) = {
        val msg = s"New message from topic: ${message.getData().toStringUtf8()}"
        F.runAsync(F.delay(println(msg)) >> queue.enqueue1(msg))(_ => IO(consumer.ack())).unsafeRunSync
      }
    }
    for {
      //Settings Provider and Credentials Provider are only required for PubSub Emulator
      channelProvider <- Stream.eval(F.delay(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))))
      credentialsProvider <- Stream.eval(F.delay(NoCredentialsProvider.create()))
      _ = println(s"Creating subscriber")
      _ <- Stream.bracket(F.delay(Subscriber.newBuilder(subscriptionName, receiver).setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build()))( subscriber => Stream.eval(F.delay(subscriber.startAsync())), subscriber => F.delay(subscriber.stopAsync()) )
      _ = println(s"Retrieving message from queue")
      message <- queue.dequeue
    } yield message
  }
}
