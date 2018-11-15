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
import cats.syntax.applicative._
import cats.syntax.traverse._
import cats.syntax.either._
import cats.instances.list._
import cats.effect.{ Async, Effect, IO }
import fs2._
import pureconfig._
import pureconfig.module.catseffect._

import PubSubOps.{ PubSubConfig, publish }

object PubSubExamplePublisher {
  // For now Publisher just generates 10 test messages to sent to the topic
  def generateMessages: List[String] = {
    val messageTemplate = "pubsubexample"
    val nums = Stream.emit(1).repeat.scan1(_ + _).take(10).toList
    nums.map(num => s"$messageTemplate$num")
  }

  def main(args: Array[String]): Unit = {
    import ExecutionContext.Implicits.global
    val messages = generateMessages
    lazy val error = IO.raiseError[String](new Exception("Unable to read topic name from configuration"))
    
    val io = for {
      config <- loadConfigF[IO, PubSubConfig]
      topicId <- config.topicId.fold(error)(_.pure[IO])
      published = publish[IO](config.projectId, topicId, messages)
      _ <- published.compile.drain
    } yield ()
    io.unsafeRunSync
  }
}
