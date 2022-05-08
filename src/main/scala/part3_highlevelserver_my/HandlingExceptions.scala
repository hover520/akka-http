package part3_highlevelserver_my

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler

/**
  * Handle exceptions
  *  - ExceptionHandler
  *  - exception handling directive
  *
  * val myHandler: ExceptionHandler = ExceptionHandler {
  *   case e: RuntimeException => ...
  *   case ie: IllegalArgumentException => ...
  * }
  *
  * handleExceptions(myHandler) {
  *   // Route which can throw     // exceptions are caught and handled
  * }
  *
  * Exceptions are not aggregated
  * Default handler returns HTTP 500
  *
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-08 18:15
  */
object HandlingExceptions extends App {

  implicit val system = ActorSystem("HandlingException")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    path("api" / "people") {
      get {
        // directive that throws some exception
        throw new RuntimeException("Getting all the people took too long")
      } ~
      post {
        parameter('id){ id =>
          if(id.length > 2)
            throw new NoSuchElementException(s"Parameter $id cannot be found in the database,TABLE FLIP!")

          complete(StatusCodes.OK)
        }
      }
    }

  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound,e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }

  // Http().bindAndHandle(simpleRoute,"localhost",8080)

  val runtimeExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: RuntimeException =>
      complete(StatusCodes.NotFound,e.getMessage)
  }
  val noSuchElementExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException =>
      complete(StatusCodes.BadRequest,e.getMessage)
  }

  val delicateHandleRoute =
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          // directive that throws some exception
          throw new RuntimeException("Getting all the people took too long")
        } ~
        handleExceptions(noSuchElementExceptionHandler){
          post {
            parameter('id){ id =>
              if(id.length > 2)
                throw new NoSuchElementException(s"Parameter $id cannot be found in the database,TABLE FLIP!")

              complete(StatusCodes.OK)
            }
          }
        }

      }
    }
  Http().bindAndHandle(delicateHandleRoute,"localhost",8080)
}
