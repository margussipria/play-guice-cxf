package org.apache.cxf.transport.play

import java.io.OutputStream

import javax.inject.{Inject, Singleton}

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import org.apache.cxf.message.Message
import play.api.http.HttpEntity
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Failure

@Singleton
class CxfController @Inject() (
  transportFactory: PlayTransportFactory,
  controllerComponents: ControllerComponents,
  messageExtractor: MessageExtractor
)(implicit ec: ExecutionContext, materializer: Materializer) extends AbstractController(controllerComponents) {

  val maxRequestSize: Int = 1024 * 1024

  /**
   * Handler with a strict entity response.
   *
   * Strict entities are contained entirely in memory.
   */
  def handleStrict(path: String = ""): Action[RawBuffer] = handleRequest(path)(toStrictResult)

  /**
   * Handler with a streamed entity response.
   *
   * This entity will be close delimited.
   */
  def handleStreamed(path: String = ""): Action[RawBuffer] = handleRequest(path)(toStreamedResult)

  /**
   * Handler with a chunked entity response.
   */
  def handleChunked(path: String = ""): Action[RawBuffer] = handleRequest(path)(toChunkedResult)

  protected def handleRequest(path: String = "")(createResult: (Int, Source[ByteString, _], String) => Future[Result]): Action[RawBuffer] = {
    Action.async(parse.raw(maxRequestSize)) { implicit request =>
      val messagePromise = Promise[Message]()

      Future {
        val message = messageExtractor.extractMessage
        message.put(PlayDestination.PLAY_MESSAGE_PROMISE, messagePromise)

        getDestination.dispatchMessage(message)
      } andThen {
        case Failure(exception) =>
          messagePromise.tryFailure(exception)
      }

      messagePromise.future flatMap { message =>

        val source = StreamConverters.asOutputStream().mapMaterializedValue(outputStream => Future {
          val delayedOutputStream = message.getContent(classOf[OutputStream]).asInstanceOf[DelayedOutputStream]

          delayedOutputStream.flush()
          delayedOutputStream.setTarget(outputStream)
        })

        val responseCode = Option(message.get(Message.RESPONSE_CODE)) map (_.toString) map (_.toInt) getOrElse OK
        val contentType = message.get(Message.CONTENT_TYPE).asInstanceOf[String]

        createResult(responseCode, source, contentType)
      }
    }
  }

  protected def toStrictResult(responseCode: Int, source: Source[ByteString, _], contentType: String): Future[Result] = {
    source.runWith(Sink.reduce[ByteString](_ ++ _)) map { data =>
      Status(responseCode).sendEntity(
        HttpEntity.Strict(data, Some(contentType))
      )
    }
  }

  protected def toStreamedResult(responseCode: Int, source: Source[ByteString, _], contentType: String): Future[Result] = {
    Future {
      Status(responseCode).sendEntity(
        HttpEntity.Streamed(source, None, Some(contentType))
      )
    }
  }

  protected def toChunkedResult(responseCode: Int, source: Source[ByteString, _], contentType: String): Future[Result] = {
    Future {
      Status(responseCode).chunked(source).as(contentType)
    }
  }

  private def endpointAddress(implicit request: Request[RawBuffer]): String = "play://" + request.host + request.path

  def getDestination(implicit request: Request[RawBuffer]): PlayDestination = {
    Option(transportFactory.getDestination(endpointAddress)).orElse(
      Option(transportFactory.getDestination(request.path))
    ) getOrElse {
      throw new IllegalArgumentException(s"Destination not found: [$endpointAddress] ${transportFactory.getDestinationsDebugInfo}")
    }
  }
}
