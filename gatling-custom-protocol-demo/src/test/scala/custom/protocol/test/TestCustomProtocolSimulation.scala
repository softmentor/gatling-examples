package custom.protocol.test

/**
 * Created with IntelliJ IDEA.
 * User: jiny
 * Date: 5/9/13
 * Time: 9:52 PM
 * To change this template use File | Settings | File Templates.
 */

import akka.actor.{ActorRef, Props}
import com.custom._
import io.gatling.core.Predef._
import io.gatling.core.action.Chainable
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.Protocols
import io.gatling.core.result.message.{KO, OK}
import io.gatling.core.result.writer.{DataWriter, RequestMessage}

import scala.concurrent.duration._

//import scalaj.collection.Imports._
//import scala.collection.JavaConversions._

import scala.collection.convert.wrapAsJava

class TestCustomProtocolSimulation extends Simulation {

  val mine = new ActionBuilder {
    def build(next: ActorRef, protocols: Protocols) = {
      system.actorOf(Props(new MyAction(next)))
    }
  }
  val userLog = csv("user_credentials.csv").circular
  val scn = scenario("My custom protocol test")
    .feed(userLog)
    .repeat(2) {
    exec(mine)
  }

  /*setUp(scn.inject(ramp(3 users) over (10 seconds)))
      //Assert the output max time is less than 50ms and 95% of requests were successful
      .assertions(global.responseTime.max.lessThan(50),global.successfulRequests.percent.greaterThan(95))*/

  setUp(
    scn.inject(
      nothingFor(4 seconds), // 1
      atOnceUsers(10), // 2
      rampUsers(3) over (5 seconds), // 3
      constantUsersPerSec(5) during (5 seconds), // 4
      constantUsersPerSec(5) during (5 seconds) randomized, // 5
      rampUsersPerSec(10) to 20 during (1 minutes), // 6
      rampUsersPerSec(10) to 20 during (1 minutes) randomized, // 7
      splitUsers(10) into (rampUsers(2) over (10 seconds)) separatedBy (2 seconds), // 8
      splitUsers(10) into (rampUsers(2) over (10 seconds)) separatedBy atOnceUsers(5), // 9
      heavisideUsers(10) over (500 milliseconds) // 10
    )
    //Assert the output max time is less than 50ms and 95% of requests were successful
  ).assertions(global.responseTime.max.lessThan(50), global.successfulRequests.percent.greaterThan(95))
}


class MyAction(val next: ActorRef) extends Chainable {


  def greet(session: Session) {
    //val username = session.attributes.apply("username")
    //val password = session.attributes("password") // need not call apply it is implicit

    val delegate = new SayHello
    //using scalaj to convert from scala map to java map
    delegate.echo("Hi", wrapAsJava.mapAsJavaMap(session.attributes))
  }

  def execute(session: Session) {
    var start: Long = 0L
    var end: Long = 0L
    var status: Status = OK
    var errorMessage: Option[String] = None

    try {
      start = System.currentTimeMillis;
      greet(session) // Call any custom code you wish, say an API call
      end = System.currentTimeMillis;
    } catch {
      case e: Exception =>
        errorMessage = Some(e.getMessage)
        logger.error("FOO FAILED", e)
        status = KO
    } finally {
      val requestStartDate, requestEndDate = start
      val responseStartDate, responseEndDate = end
      val requestName = "Test Scenario"
      val message = errorMessage
      val extraInfo = Nil
      DataWriter.dispatch(RequestMessage(
        session.scenarioName,
        session.userId,
        session.groupHierarchy,
        requestName,
        requestStartDate,
        requestEndDate,
        responseStartDate,
        responseEndDate,
        status,
        message,
        extraInfo))
      next ! session
    }
  }
}
