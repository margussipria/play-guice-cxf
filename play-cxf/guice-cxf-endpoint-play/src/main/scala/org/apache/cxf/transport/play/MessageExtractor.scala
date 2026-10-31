package org.apache.cxf.transport.play

import java.io.InputStream

import org.apache.pekko.util.ByteString
import com.google.inject.ImplementedBy
import org.apache.cxf.message.{Message, MessageImpl}
import play.api.mvc.{RawBuffer, Request}

import scala.jdk.CollectionConverters._

@ImplementedBy(value = classOf[MessageExtractorImpl])
trait MessageExtractor {
  def extractMessage(implicit request: Request[RawBuffer]): Message
}

class MessageExtractorImpl extends MessageExtractor {

  def extractMessage(implicit request: Request[RawBuffer]): Message = {
    val msg: Message = new MessageImpl
    msg.put(Message.HTTP_REQUEST_METHOD, request.method)
    msg.put(Message.REQUEST_URL, request.path)
    msg.put(Message.QUERY_STRING, request.rawQueryString)
    msg.put(Message.PROTOCOL_HEADERS, headersAsJava)
    msg.put(Message.CONTENT_TYPE, request.headers.get(Message.CONTENT_TYPE).orNull)
    msg.put(Message.ACCEPT_CONTENT_TYPE, request.headers.get(Message.ACCEPT_CONTENT_TYPE).orNull)
    msg.put("Remote-Address", request.remoteAddress)

    request.body.asBytes() foreach { arr: ByteString =>
      msg.setContent(classOf[InputStream], arr.iterator.asInputStream)
    }

    msg
  }

  protected def headersAsJava(implicit request: Request[RawBuffer]): java.util.Map[String, java.util.List[String]] = {
    request.headers.toMap.map { case (key, value) => (key, value.asJava) }.asJava
  }
}
