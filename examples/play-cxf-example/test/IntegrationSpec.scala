
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import play.api.libs.ws.WSClient
import play.api.test.Helpers._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application
 */
class IntegrationSpec extends PlaySpec with GuiceOneServerPerTest with ScalaFutures with IntegrationPatience {

  "Application" should {

    "send 404 on a bad request" in {
      implicit val wsClient: WSClient = app.injector.instanceOf(classOf[WSClient])

      val futureResult = wsUrl("/testing").get()
      val result = futureResult.futureValue

      result.status must be (NOT_FOUND)
    }

    "work from within a browser" in {
      implicit val wsClient: WSClient = app.injector.instanceOf(classOf[WSClient])

      val futureResult = wsUrl("/").get()
      val response = futureResult.futureValue

      response.status must be (OK)
    }
  }
}
