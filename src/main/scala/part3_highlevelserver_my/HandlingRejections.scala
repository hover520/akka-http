package part3_highlevelserver_my

import akka.actor.ActorSystem
import akka.http.javadsl.server.MethodRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MissingQueryParamRejection, Rejection, RejectionHandler}
import akka.stream.ActorMaterializer

/**
  * Rejections
  *  If a request doesn't match a filter directive,it's rejected
  *    - rejection = pass the request to another branch in the routing tree
  *    - a rejection is NOT a failure
  * Rejections are aggregated
  *  get {             ---> if request is not a GET , add rejection
  *    // Route1
  *  } ~
  *  post {            ---> if request is not a POST, add rejection
  *    // Route2
  *  } ~
  *  parameter('myParam){ myParam =>
  *    // Route3       ---> if request has a query param, clear the rejections list (other rejections might be added within)
  *  }
  * We can choose how to handle the rejection list
  *
  * Handle rejections
  *  - rejection handler = function between a rejection list and a route
  *  - rejection handling directives
  *
  *  val myHandler: RejectionHandler = rejections => ...
  *
  *  handleRejections(myHandler) {
  *     // Route with potential rejections    // rejections are "caught" and handled
  *  }
  *
  *  Implicit rejection handlers and handlers and builders
  *
  *  implicit val myDefaultHandler = RejectionHandler
  *     .newBuilder()
  *     .handle {                               // careful with ordering
  *       case r: MethodRejection => ...
  *     }
  *     .handleAll[MissingQueryParameterRejection] { r =>   // alternative
  *       ...
  *     }
  *     .handleNotFound { ... }     // return a Route in any case
  *     .result()
  *
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-08 16:55
  */
object HandlingRejections extends App {

  implicit val system = ActorSystem("HandlingRejections")
  implicit val materializer = ActorMaterializer()

  val simpleRoute =
    path("api" / "myEndpoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      parameter('id) {_ =>
        complete(StatusCodes.OK)
      }
    }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections : Seq[Rejection] =>
    println(s" I have encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenRequestHandler: RejectionHandler = { rejections : Seq[Rejection] =>
    println(s" I have encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler) {  // handle rejections from the top level
      // define server logic inside
      path("api" / "myEndpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
        post {
          handleRejections(forbiddenRequestHandler) {  // handle rejections WITHIN
            parameter('myParam) {_ =>
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }

  // Http().bindAndHandle(simpleRouteWithHandlers,"localhost",8080)

  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle{
      case m : MissingQueryParamRejection =>
        println(s"I got a query param rejection: $m")
        complete("Rejected query param!")
    }
    .handle{
      case m : MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected method!")
    }
    .result()

  Http().bindAndHandle(simpleRoute,"localhost",8080)


}
