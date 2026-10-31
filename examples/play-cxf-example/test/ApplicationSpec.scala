
import controllers.Application
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures {

  "Application" should {

    "render the index page" in {

      val request = FakeRequest(GET, "/")

      val home = app.injector.instanceOf(classOf[Application]).index(request)

      status(home) must be (OK)
      contentType(home) must contain ("text/plain")
      contentAsString(home) must be ("Your new application is ready.")
    }
  }
}
