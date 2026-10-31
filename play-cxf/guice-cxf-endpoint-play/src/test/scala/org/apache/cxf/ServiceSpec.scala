package org.apache.cxf

import java.io.File
import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.ConcurrentLinkedQueue

import jakarta.xml.ws.BindingProvider

import com.google.inject.{AbstractModule, Provider}
import org.apache.cxf.message.Message
import org.apache.cxf.phase.{AbstractPhaseInterceptor, Phase}
import org.apache.cxf.transport.play.EndpointModule
import org.apache.date_and_time_soap_http.{AskTimeRequest, DateAndTime}
import org.scalactic.StringNormalizations.lowerCased
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{freespec, matchers}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModuleConversions}
import play.api.test.{Helpers, TestServer, WsTestClient}
import play.api.{Application, Configuration, Environment}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

class ServiceSpec extends freespec.AnyFreeSpec with GuiceableModuleConversions with matchers.should.Matchers with ScalaFutures {

  type Builder = GuiceApplicationBuilder

  class DateAndTimeServiceModule extends EndpointModule(false) {

    override def configure(): Unit = {
      bindEndpoint("DateAndTimeServiceStrict", Seq.empty)
      bindEndpoint("DateAndTimeServiceStreamed", Seq.empty)
      bindEndpoint("DateAndTimeServiceChunked", Seq.empty)
    }
  }

  class DateAndTimeClientModule extends ClientModule(false) {
    override def configure(): Unit = {
      bindClient[DateAndTime]("org.apache.date_and_time_soap_http.DateAndTime", Seq.empty)
    }
  }

  private def withApplication(f: Builder => Builder = identity)(testCode: (Application, Int) => Any): Any = {

    val builder = GuiceApplicationBuilder(eagerly = true)
      .bindings(
        fromGuiceModule(new AbstractModule {
          override def configure(): Unit = {
            bind(classOf[Clock]).toInstance(Clock.systemUTC())

            install(new DateAndTimeServiceModule)
            install(new DateAndTimeClientModule)
          }
        })
      )

      .appRoutes( app => {
        case ("GET" | "POST", "/service/strict") =>
          app.injector.instanceOf[org.apache.cxf.transport.play.CxfController].handleStrict()
        case ("GET" | "POST", "/service/streamed") =>
          app.injector.instanceOf[org.apache.cxf.transport.play.CxfController].handleStreamed()
        case ("GET" | "POST", "/service/chunked") =>
          app.injector.instanceOf[org.apache.cxf.transport.play.CxfController].handleChunked()
      })
      .loadConfig(
        Configuration.load(Environment.simple(new File(getClass.getResource("/application.conf").getPath)))
      )


    val application = f(builder).build()

    Helpers.runningWithPort(TestServer(Helpers.testServerPort, application)) { port =>
      testCode(application, port)
    }
  }

  private def timeService(application: Application, port: Int): DateAndTime = {
    val service = application.injector.instanceOf[DateAndTime]
    service.asInstanceOf[BindingProvider].getRequestContext.put(
      BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
      s"http://localhost:$port/service/strict"
    )
    service
  }

  "A time server" - {
    "should return correct time" in withApplication { builder =>
      builder.overrides(
        fromGuiceModule(new AbstractModule {
          override def configure(): Unit = {
            bind(classOf[Clock]).toInstance(
              Clock.fixed(Instant.parse("2013-10-27T22:04:00.246Z"), ZoneOffset.UTC)
            )
          }
        })
      )
    } { (app, port) =>

      val request = new AskTimeRequest()
      request.setTimeZone("Europe/Helsinki")

      timeService(app, port)
        .askTime(request).getResponse.toString should be ("2013-10-28T00:04:00.246")
    }

    "should respond to multiple requests with correct time" in withApplication { builder =>
      builder.overrides(
        fromGuiceModule(new AbstractModule {
          override def configure(): Unit = {
            bind(classOf[Clock]).toInstance(
              Clock.fixed(Instant.parse("2013-10-27T22:04:00.000Z"), ZoneOffset.UTC)
            )
          }
        })
      )
    } { (app, port) =>

      val request = new AskTimeRequest()
      request.setTimeZone("Europe/Helsinki")

      val service = timeService(app, port)
      val queue = interceptResponseCode(service)

      val end = 25

      collection.parallel.immutable.ParSeq.range(1, end + 1).map { _ => service.askTime(request) } foreach { response =>
        response.getResponse.toString should be ("2013-10-28T00:04:00.000")
      }

      queue should have size end
      queue should contain only "200"
    }

    "should return fault with correct http status" in withApplication { builder =>
      builder.overrides(
        fromGuiceModule(new AbstractModule {
          override def configure(): Unit = {
            bind(classOf[Clock]).toProvider(new Provider[Clock] {
              def get: Clock = new Clock {
                override def getZone: ZoneId = throw new Exception("Not Implemented")
                override def withZone(zone: ZoneId): Clock = throw new Exception("No Clock")
                override def instant(): Instant = throw new Exception("Not Implemented")
              }
            })
          }
        })
      )
    } { (app, port) =>
      val service = timeService(app, port)

      val request = new AskTimeRequest()
      request.setTimeZone("Europe/Helsinki")

      val queue = interceptResponseCode(service)

      val fault = intercept[org.apache.date_and_time_soap_http.AskTimeFault] {
        service.askTime(request)
      }

      fault.getFaultInfo.getErrorCode should be ("667")
      fault.getFaultInfo.getErrorMessage should be ("Test Error Message")

      queue should have size 1
      queue should contain ("500")
    }

    "when asking wsdl" - {

      "should return content as strict" in withApplication (identity) { (_, port) =>

        WsTestClient.withClient { client =>

          val result = Await.result(
            client.url(s"http://localhost:$port/service/strict?wsdl").get(),
            10 seconds
          )

          result.status should be (Helpers.OK)
          (result.headers should contain key ("Content-Length")) (after being lowerCased)
        }
      }

      "should return content as streamed" in withApplication (identity) { (_, port) =>

        WsTestClient.withClient { client =>

          val result = Await.result(
            client.url(s"http://localhost:$port/service/streamed?wsdl").get(),
            10 seconds
          )

          result.status should be (Helpers.OK)
          (result.headers should not contain key ("Content-Length")) (after being lowerCased)
          (result.headers should not contain key ("Transfer-Encoding")) (after being lowerCased)
        }
      }

      "should return content as chunked" in withApplication (identity) { (_, port) =>

        WsTestClient.withClient { client =>

          val result = Await.result(
            client.url(s"http://localhost:$port/service/chunked?wsdl").get(),
            10 seconds
          )

          result.status should be (Helpers.OK)
          (result.headers should contain key ("Transfer-Encoding")) (after being lowerCased)

          result.headers("Transfer-Encoding") should contain ("chunked")
        }
      }

    }
  }

  def interceptResponseCode(service: AnyRef): ConcurrentLinkedQueue[String] = {
    val queue = new ConcurrentLinkedQueue[String]

    val client = org.apache.cxf.frontend.ClientProxy.getClient(service)
    client.getInInterceptors.add(new ResponseCodeInterceptor(queue))

    queue
  }
}

class ResponseCodeInterceptor(queue: ConcurrentLinkedQueue[String]) extends AbstractPhaseInterceptor[Message](Phase.RECEIVE) {
  def handleMessage(message: Message): Unit = {
    queue.add(message.get(Message.RESPONSE_CODE).toString)
  }
}
