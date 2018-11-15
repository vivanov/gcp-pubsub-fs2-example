# gcp-pubsub-fs2-example
*Example of GCP PubSub operations wrapped in FS2 streams functional API*

This repository provides examples of asynchronous publisher (standalone app) and subscriber (as part of [Http4s](https://http4s.org) service) using [fs2](http://fs2.io) and [Cats Effect](https://typelevel.org/cats-effect/) libraries as wrapper on top native Java client API.  

First it's necessary to install Google Cloud SDK and make sure Client Library is added as a dependency as described in [Cloud Client Libraries for the Cloud Pub/Sub API](https://cloud.google.com/pubsub/docs/reference/libraries)

In order to run publisher/subscriber Project, Topic and Subscription for GCP PubSub have to be created as described in official [Quickstart Client Libraries Guide](https://cloud.google.com/pubsub/docs/quickstart-client-libraries) and [Managing Topic and Subscription Guide](https://cloud.google.com/pubsub/docs/admin#pubsub-list-topics-protocol).

Below is an example of HTTP PUT request to create topic and corresponding response:

Command:
```
curl -X PUT http://localhost:8085/v1/projects/pubs-tst/topics/my-tst-topic
```

Example output:
```
{
  "name": "projects/pubs-tst/topics/my-tst-topic"
}
```

And then example of HTTP PUT request to create subscription to the topic above:

Command:
```
curl -H "Content-Type: application/json" -d '{"topic": "projects/pubs-tst/topics/my-tst-topic"}' -X PUT http://localhost:8085/v1/projects/pubs-tst/subscriptions/my-tst-subscription
```

Example output:
```
{
  "name": "projects/pubs-tst/subscriptions/my-tst-subscription",
  "topic": "projects/pubs-tst/topics/my-tst-topic",
  "pushConfig": {
  },
  "ackDeadlineSeconds": 10,
  "messageRetentionDuration": "604800s"
}
```

Then publisher and subscriber have to use project, topic and subsctiption created above. For now source files have to be edited manually, in future they have to be externalized into configuration files.  

Publisher: `projectId` and `topicId` in `PubSubExamplePublisher.publish` method.

Subscriber: `projectId` and `subscriptionId` in `PubSubExampleService.messages` method.

Publisher/Subscriber were tested on GCP PubSub Emulator, which has to be set up as describerd in [Cloud PubSub Emulator Guide](https://cloud.google.com/pubsub/docs/emulator)

SBT commands:

`publisher/run` - runs Publisher which sends 10 test messages to the Topic

`subscriber-server/run` - starts Http4s service, test route for subscription to retrieve test messages from Topic is available under `/pubsub` path. Server also serves websocket route to get messages both from PubSub Topic and from client (see below).

Both publisher and subscriber process messages asynchronously, but different approaches are used to deal with callbacks. For publisher it's called only once (per message sent) as described in [FS2 Guide - Talking to the external world: Callbacks invoked once](http://fs2.io/guide.html#asynchronous-effects-callbacks-invoked-once), for subscriber it's called multiple times as described in [FS2 Guide - Talking to the external world: Callbacks invoked multiple times](http://fs2.io/guide.html#asynchronous-effects-callbacks-invoked-multiple-times)

There's also client side application which connects to the server via Websockets. It allows to both display messages received from the PubSub topic by server side of the subscriber and send/display text messages to the server.

In order to build client side application it's neccessary to install NVM as described in [Installation section](https://github.com/creationix/nvm#installation).

Then to download Node dependencies and generate required JS artifacts it's neccessary to execute following SBT command:
`subscriber-client/fastOptJS::webpack`

After that, having server running, client application is available by following URL by default: `http://localost:8080/index.html`

This example project is based on and mostly assembled from:

 - [http4s](https://github.com/http4s/http4s) and more specifically WebSocket example (versions [0.18](https://github.com/http4s/http4s/blob/release-0.18.x/examples/blaze/src/main/scala/com/example/http4s/blaze/BlazeWebSocketExample.scala) and [0.20](https://github.com/http4s/http4s/blob/master/examples/blaze/src/main/scala/com/example/http4s/blaze/BlazeWebSocketExample.scala))
 - [scalajs-react](https://github.com/japgolly/scalajs-react) and more specifically [WebSocket client example](https://github.com/japgolly/scalajs-react/blob/master/gh-pages/src/main/scala/ghpages/examples/WebSocketsExample.scala)
 - [http4s-scalajsxample](https://github.com/ChristopherDavenport/http4s-scalajsexample) and its [JSApplication example](https://github.com/ChristopherDavenport/http4s-scalajsexample/blob/master/backend/src/main/scala/org/http4s/scalajsexample/JSApplication.scala)
 - [http4sapp](https://github.com/objektwerks/typelevel/blob/master/src/main/scala/objektwerks/app/Http4sApp.scala) and [pwa.http4s](https://github.com/objektwerks/pwa.http4s)