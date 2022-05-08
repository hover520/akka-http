package part3_highlevelserver_my

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
/**
  * @author : HoverKan
  * @version : 1.0.0 :: 2022-05-07 21:48
  */

case class Person(pin: Int,name : String)

trait PersonStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val personFormat = jsonFormat2(Person)
}

object PersonDB {
  case class CreatePerson(person: Person)
  case object FindAllPersons
  case class FindPerson(pin:Int)
  case class CreateSuccess(pin:Int)
}

class PersonDB extends Actor with ActorLogging {
  import PersonDB._

  var people:List[Person] = List()
  var pd : Map[Int,Person] = Map()
  override def receive: Receive = {
    case FindAllPersons =>
      log.info(s"Find All Persons count : ${people.size} ")
      sender() ! people
    case CreatePerson(person) =>
      log.info(s"Creating a person with ${person.pin} and ${person.name}")
      people = people :+ person
      pd = pd + (person.pin -> person)
      sender() ! CreateSuccess(person.pin)
    case FindPerson(pin) =>
      log.info(s"Find the person with $pin")
      val personOption = pd.get(pin)
      sender() ! personOption
  }
}

object HighLevelExercise extends App with PersonStoreJsonProtocol{

  implicit val system = ActorSystem("HighLevelExercise")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  /**
    * Exercise :
    *
    * - GET /api/people: retrieve ALL th people you have registered
    * - GET /api/people/pin : retrieve the person with that PIN, return as JSON
    * - GET /api/people?pin=X (same)
    * - (harder) POST /api/people with a JSON payload denoting a Person,add that person to your database
    *   - extract the request's payload (entity)
    *     - extract the request
    *     - process the entity's data
    */
  val personDb = system.actorOf(Props[PersonDB],"personDb")
  // set up
  import PersonDB._
  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie")
  )
//  println(people.toJson.prettyPrint)

  people.foreach(person => personDb ! CreatePerson(person))
//  personDb ! FindAllPersons

  implicit val timeout = Timeout(3 seconds)
  val personRoute =
    pathPrefix("api" / "people") {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) {pin =>
          // 1: fetch the person with the pin
          println(pin)
          val personFuture: Future[Option[Person]] = (personDb ? FindPerson(pin)).mapTo[Option[Person]]
          val personEntity = personFuture.map { person =>
            HttpEntity(
              ContentTypes.`application/json`,
              person.getOrElse(Person(0,"111")).toJson.prettyPrint
            )
          }
          complete(personEntity)
        } ~
        pathEndOrSingleSlash {
          // 2: fetch all the people
          val personsFuture: Future[List[Person]] = (personDb ? FindAllPersons).mapTo[List[Person]]
          val personsEntity = personsFuture.map { persons =>
            HttpEntity(
              ContentTypes.`application/json`,
              persons.toJson.prettyPrint
            )
          }
          complete(personsEntity)
        }

      } ~
      (post & pathEndOrSingleSlash & extractRequest & extractLog) {(request,log)=>
        // 3： insert a person into my "database"
        val entityStrictFuture = request.entity.toStrict(3 seconds)
        val entityFuture: Future[Person] = entityStrictFuture.map(_.data.utf8String.parseJson.convertTo[Person])
        var code = StatusCodes.OK
        // "side-effects"
        entityFuture.onComplete {
          case Success(person) =>
            log.info(s"Get $person , and insert into DB  ")
            val result: Future[CreateSuccess] = (personDb ? CreatePerson(person)).mapTo[CreateSuccess]
            result.onComplete{
              case Success(result) =>
                log.info(s"${result.pin} created")
              case Failure(exception) =>
                log.warning(s"failed  created with : $exception")
            }
          case Failure(ex) =>
            log.warning(s"Something failed with fetching the person from the entity: $ex")
        }
        complete(entityFuture
        .map(_ => StatusCodes.OK)
        .recover{
          case _ => StatusCodes.InternalServerError
        })
      }
    }

  Http().bindAndHandle(personRoute,"localhost",8080)



}
