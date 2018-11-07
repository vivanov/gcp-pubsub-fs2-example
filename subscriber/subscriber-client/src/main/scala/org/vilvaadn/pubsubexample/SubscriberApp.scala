package org.vilvaadn.pubsubexample

import japgolly.scalajs.react.ReactDOM
import org.scalajs.dom
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExport
import SubscriberClient.WebSocketsApp

object SubscriberApp extends JSApp {

  @JSExport
  override def main(): Unit = {
    println(s"HEY")
    //SubscriberRouter.router().renderIntoDOM(dom.document.getElementById("template-app"))
    WebSocketsApp().renderIntoDOM(dom.document.getElementById("template-app"))
  }

}
