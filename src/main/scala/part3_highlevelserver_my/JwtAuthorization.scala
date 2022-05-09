package part3_highlevelserver_my

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import spray.json._
/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-09 10:21
  */
object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)
  implicit val loginRequestFormat = jsonFormat2(LoginRequest)
}
object JwtAuthorization extends App {

  implicit val system = ActorSystem("JwtAuthorization")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import SecurityDomain._

  val superSecretPasswordDb = Map(
    "admin" -> "admin",
    "hover" -> "kam"
  )


}
