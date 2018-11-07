package org.vilvaadn.pubsubexample

import org.scalajs.dom

import japgolly.scalajs.react.extra.router._

import japgolly.scalajs.react.vdom.html_<^._

import SubscriberClient.WebSocketsApp

object SubscriberRouter {

  sealed trait MenuItems
  case object Home extends MenuItems
  case object PubSubExample extends MenuItems

  val routerConfig = RouterConfigDsl[MenuItems].buildConfig { dsl =>
    import dsl._

    (emptyRule
    | staticRoute(root, Home)  ~> render(<.h1("Welcome!"))
    | staticRoute("#pubsubexample", PubSubExample) ~> renderR(r => WebSocketsApp())
    ).notFound(redirectToPage(Home)(Redirect.Replace))
  }


  /*
  val baseUrl = 
    dom.window.location.hostname match {
      case "localhost" | "127.0.0.1" | "0.0.0.0" =>
        BaseUrl.fromWindowUrl(_.takeWhile(_ != '#'))
      case _ =>
        BaseUrl.fromWindowOrigin / "pubsub-example"
    }
   */

  val baseUrl = BaseUrl.fromWindowOrigin
  val router = Router(baseUrl, routerConfig)
}
