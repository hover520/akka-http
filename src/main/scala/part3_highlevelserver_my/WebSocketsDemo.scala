package part3_highlevelserver_my

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source

/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-09 08:28
  */
object WebSocketsDemo extends App {

  implicit val system = ActorSystem("WebSocketsDemo")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // Message: TextMessage vs BinaryMessage

  val textMessage = TextMessage(Source.single("hello via a text message"))
//  val binaryMessage = BinaryMessage(Source.single("hello via a binary message"))



}
