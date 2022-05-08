package part3_highlevelserver_my

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-06 08:41
  */
object HighLevelIntro extends App {

  implicit val system = ActorSystem("HighLevelIntro")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  // directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") {  // DIRECTIVE
      complete(StatusCodes.OK)  // DIRECTIVE
    }

  val pathGetRoute: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      }
    }

  // chaining directives with ~
  val chainedRoute: Route =
    path("myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~ // ~ is very important
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~
    path("home") {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |    <body>
            |       Hello From the high level Akka HTTP!
            |    </body>
            |</html>
            |""".stripMargin
        )
      )
    }// Routing tree

  Http().bindAndHandle(chainedRoute,"localhost",8080)
  // chainedRoute implicitly converted to flow

}
