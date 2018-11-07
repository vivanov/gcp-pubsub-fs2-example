# gcp-pubsub-fs2-example
*Example of GCP PubSub operations wrapped in FS2 streams functional API*

This repository provides examples of asynchronous publisher (standalone app) and subscriber (as part of [Http4s](https://http4s.org) service) using [fs2](http://fs2.io) and [Cats Effect](https://typelevel.org/cats-effect/) libraries as wrapper on top native Java client API.  

First it's necessary to install Google Cloud SDK and make sure Client Library is added as a dependency as described in [Cloud Client Libraries for the Cloud Pub/Sub API](https://cloud.google.com/pubsub/docs/reference/libraries)

In order to run publisher/subscriber Project, Topic and Subscription for GCP PubSub have to be created as described in official [Quickstart Client Libraries Guide](https://cloud.google.com/pubsub/docs/quickstart-client-libraries) and [Managing Topic and Subscription Guide](https://cloud.google.com/pubsub/docs/admin#pubsub-list-topics-protocol).

Below is an example of HTTP PUT request to create topic and corresponding response:

```
curl -X PUT http://localhost:8085/v1/projects/pubs-tst/topics/my-tst-topic

{
  "name": "projects/pubs-tst/topics/my-tst-topic"
}
```
And then example of HTTP PUT request to create subscription to the topic above:

```
curl -H "Content-Type: application/json" -d '{"topic": "projects/pubs-tst/topics/my-tst-topic"}' -X PUT http://localhost:8085/v1/projects/pubs-tst/subscriptions/my-tst-subscription
{
  "name": "projects/pubs-tst/subscriptions/my-tst-subscription",
  "topic": "projects/pubs-tst/topics/my-tst-topic",
  "pushConfig": {
  },
  "ackDeadlineSeconds": 10,
  "messageRetentionDuration": "604800s"
}
```

Publisher/Subscriber were tested on GCP PubSub Emulator, which has to be set up as describerd in [Cloud PubSub Emulator Guide](https://cloud.google.com/pubsub/docs/emulator)

`publisher/run` - runs Publisher which sends 10 test messages to the Topic

`subscriber-server/run` - starts Http4s service, test route for subscription to retrieve test messages from Topic is available under `/pubsub` path.

Both publisher and subscriber process messages asynchronously, but different approaches are used to deal with callbacks. For publisher it's called only once (per message sent) as described in [FS2 Guide - Talking to the external world: Callbacks invoked once](http://fs2.io/guide.html#asynchronous-effects-callbacks-invoked-once), for subscriber it's called multiple times as described in [FS2 Guide - Talking to the external world: Callbacks invoked multiple times](http://fs2.io/guide.html#asynchronous-effects-callbacks-invoked-multiple-times)

