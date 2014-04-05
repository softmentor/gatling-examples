package custom.protocol.test

/**
 * Created with IntelliJ IDEA.
 * User: jiny
 * Date: 5/9/13
 * Time: 9:52 PM
 * To change this template use File | Settings | File Templates.
 */

import io.gatling.core.Predef._
import scala.concurrent.duration._
import akka.actor.ActorRef;
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.action.{Chainable, system}
import bootstrap._
import assertions._
import akka.actor.Props
import io.gatling.core.result.message.{RequestMessage, KO, OK}
import io.gatling.core.result.writer.DataWriter
import com.custom._
import scalaj.collection.Imports._

class TestCustomProtocolSimulation extends Simulation {

  val mine = new ActionBuilder {
    def build(next: ActorRef) = system.actorOf(Props(new MyAction(next)))
  }
  val userLog = csv("user_credentials.csv").queue
  val scn = scenario("My custom protocol test")
    .feed(userLog)
    .repeat(2) {
                 exec(mine)
               }

  setUp(scn.inject(ramp(3 users) over (10 seconds)))
      //Assert the output max time is less than 50ms and 95% of requests were successful
      .assertions(global.responseTime.max.lessThan(50),global.successfulRequests.percent.greaterThan(95))
}


class MyAction(val next: ActorRef) extends Chainable {


  def greet(session: Session) {
    //val username = session.attributes.apply("username")
    //val password = session.attributes("password") // need not call apply it is implicit

    val delegate = new SayHello
    //using scalaj to convert from scala map to java map
    delegate.echo("Hi", session.attributes.asJava)
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
      DataWriter.tell(
        RequestMessage(session.scenarioName, session.userId, session.groupStack, "Test Scenario",
                       start, start, end, end,
                       status, errorMessage, Nil))
      next ! session
    }
  }
}
