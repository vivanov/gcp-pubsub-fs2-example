package org.vilvaadn.pubsubexample

import com.google.protobuf.ByteString
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.ProjectTopicName
import com.google.pubsub.v1.Topic
import com.google.pubsub.v1.Subscription
import com.google.pubsub.v1.PushConfig

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures

import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.pubsub.v1.TopicAdminClient
import com.google.cloud.pubsub.v1.SubscriptionAdminClient

import com.google.api.gax.rpc.ApiException
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{ TransportChannelProvider, FixedTransportChannelProvider }
import com.google.api.gax.core.{ CredentialsProvider, NoCredentialsProvider }

import io.grpc.{ ManagedChannelBuilder, ManagedChannel }

import scala.concurrent.ExecutionContext

import cats.syntax.functor._
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.syntax.either._
import cats.instances.list._
import cats.effect.{ Async, Effect, IO }
import fs2._
import fs2.async.mutable.Queue
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger


object PubSubOps {
  case class PubSubConfig(projectId: String, topicId: Option[String], subscriptionId: Option[String])

  private def publishMessage[F[_]](publisher: Publisher)(message: String)(logger: Logger[F])(implicit F: Effect[F], ec: ExecutionContext): F[ApiFuture[String]] = for {
    _ <- logger.info(s"Publishing message: $message")
    data = ByteString.copyFromUtf8(message)
    pubsubMessage = PubsubMessage.newBuilder().setData(data).build()
    future <- F.delay(publisher.publish(pubsubMessage))
  } yield future

  private def publishMessages[F[_]](publisher: Publisher)(messages: List[String])(logger: Logger[F])(implicit F: Effect[F], ec: ExecutionContext): F[List[ApiFuture[String]]] =
    messages.traverse[F, ApiFuture[String]](message => publishMessage(publisher)(message)(logger))

  private def apiFutureCallback[F[_]](future: ApiFuture[String])(logger: Logger[F])(implicit F: Effect[F], ec: ExecutionContext) = Async[F].async[String] { (cb: Either[Throwable, String] => Unit) => ApiFutures.addCallback(future, new ApiFutureCallback[String]() {
    override def onFailure(error: Throwable) = {
      val logged = error match {
        case apiException: ApiException =>
          logger.info(s"ApiException was thrown while publishing message, code: ${apiException.getStatusCode().getCode()}, retryable: ${apiException.isRetryable()}, message: ${apiException.getMessage}")
        case _ =>
          logger.info(s"Error publishing message: ${error.getMessage()}")
      }
      F.runAsync(logged)(_ => IO.unit).unsafeRunSync // No asyncF available till cats-effect 1.0.0 (crying)
      cb(error.asLeft[String]) 
    }

    override def onSuccess(messageId: String) = {
      val logged = logger.info(s"Message sucessfully published, message Id: $messageId")
      F.runAsync(logged)(_ => IO.unit).unsafeRunSync // No asyncF available till cats-effect 1.0.0 (crying)
      cb(messageId.asRight[Throwable])
    }
  })}


  def withTopicAdminClient[F[_], A](use: TopicAdminClient => Stream[F, A])(implicit F: Effect[F], ec: ExecutionContext): Stream[F, A] =
    Stream.bracket(F.delay(TopicAdminClient.create()))(use, client => F.delay(client.close()))

  def createTopic[F[_]](projectId: String, topicId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, Topic] = 
    withTopicAdminClient { client =>
      Stream.eval(F.delay {
        val topicName = ProjectTopicName.of(projectId, topicId)
        client.createTopic(topicName)
      })
    }

  def getTopic[F[_]](projectId: String, topicId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, Topic] = 
    withTopicAdminClient { client =>
      Stream.eval(F.delay {
        val topicName = ProjectTopicName.of(projectId, topicId)
        client.getTopic(topicName)
      })
    }

  def deleteTopic[F[_]](projectId: String, topicId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, ProjectTopicName] = 
    withTopicAdminClient { client =>
      Stream.eval(F.delay {
        val topicName = ProjectTopicName.of(projectId, topicId)
        client.deleteTopic(topicName)
        topicName
      })
    }


  def withSubscriptionAdminClient[F[_], A](use: SubscriptionAdminClient => Stream[F, A])(implicit F: Effect[F], ec: ExecutionContext): Stream[F, A] =
    Stream.bracket(F.delay(SubscriptionAdminClient.create()))(use, client => F.delay(client.close()))

  def createSubscription[F[_]](projectId: String, topicId: String, subscriptionId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, Subscription] = 
    withSubscriptionAdminClient { client =>
      Stream.eval(F.delay {
        val topicName = ProjectTopicName.of(projectId, topicId)
        val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)
        client.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(), 0)
      })
    }

  def getSubscription[F[_]](projectId: String, subscriptionId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, Subscription] = 
    withSubscriptionAdminClient { client =>
      Stream.eval(F.delay {
        val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)
        client.getSubscription(subscriptionName)
      })
    }

  def deleteSubscription[F[_]](projectId: String, subscriptionId: String)(implicit F: Effect[F], ec: ExecutionContext): Stream[F, ProjectSubscriptionName] = 
    withSubscriptionAdminClient { client =>
      Stream.eval(F.delay {
        val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)
        client.deleteSubscription(subscriptionName)
        subscriptionName
      })
    }

  def publish[F[_]](projectId: String, topicId: String, messages: List[String])(logger: Logger[F])(implicit F: Effect[F], ec: ExecutionContext): Stream[F, List[String]] = {
    val topicName = ProjectTopicName.of(projectId, topicId)
    val channel = ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext(true).build()

    for {
      //Settings Provider and Credentials Provider are only required for PubSub Emulator
      channelProvider <- Stream.eval(F.delay(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))))
      credentialsProvider <- Stream.eval(F.delay(NoCredentialsProvider.create()))
      futures <- Stream.bracket(F.delay(Publisher.newBuilder(topicName).setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build()))(publisher => Stream.eval(publishMessages(publisher)(messages)(logger)), publisher => F.delay(publisher.shutdown()))
      messageIds <- Stream.eval(futures.traverse[F, String](future => apiFutureCallback(future)(logger)(F, ec)))
      _ <- Stream.eval_(F.delay(channel.shutdown()))
    } yield messageIds
  }

  def subscribe[F[_]](queue: Queue[F, String], projectId: String, subscriptionId: String)(logger: Logger[F])(implicit F: Effect[F], ec: ExecutionContext): Stream[F, String] = {
    //val hostport = sys.env("PUBSUB_EMULATOR_HOST")

    val channel = ManagedChannelBuilder.forTarget("localhost:8085").usePlaintext(true).build()
    val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)

    val receiver = new MessageReceiver {
      override def receiveMessage(message: PubsubMessage, consumer: AckReplyConsumer) = {
        val msg = s"${message.getData().toStringUtf8()}"
        F.runAsync(logger.info(s"New message from topic: $msg") >> queue.enqueue1(msg))(_ => IO(consumer.ack())).unsafeRunSync
      }
    }
    for {
      //Settings Provider and Credentials Provider are only required for PubSub Emulator
      channelProvider <- Stream.eval(F.delay(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel))))
      credentialsProvider <- Stream.eval(F.delay(NoCredentialsProvider.create()))
      _ <- Stream.eval(logger.info(s"Creating subscriber"))
      _ <- Stream.bracket(F.delay(Subscriber.newBuilder(subscriptionName, receiver).setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build()))( subscriber => Stream.eval(F.delay(subscriber.startAsync())), subscriber => F.delay(subscriber.stopAsync()) )
      _ <- Stream.eval(logger.info(s"Retrieving message from queue"))
      message <- queue.dequeue
    } yield message
  }
}
