package org.apache.date_and_time_soap_http

import java.time._
import java.util.GregorianCalendar

import javax.xml.datatype.{DatatypeConstants, DatatypeFactory, XMLGregorianCalendar}

import com.google.inject.Inject

import scala.language.implicitConversions

class DateAndTimeImpl @Inject() (
  clock: Clock
) extends DateAndTime {

  def `with`[T](value: T)(f: T => Unit): T = { f(value); value }

  override def askTime(in: AskTimeRequest): AskTimeResponse = {

    val zoneId = ZoneId.of(in.getTimeZone)

    try {
      val utcClock = clock.withZone(ZoneOffset.UTC)

      `with`(new AskTimeResponse) { response =>
        response.setResponse(ZonedDateTime.now(utcClock).withZoneSameInstant(zoneId))
      }
    } catch {
      case exception: Exception =>
        val utcClock = Clock.systemUTC()

        val fault = new ServiceFault()
        fault.setErrorCode("667")
        fault.setErrorMessage("Test Error Message")
        fault.setTimeStamp(ZonedDateTime.now(utcClock).withZoneSameInstant(zoneId))

        throw new AskTimeFault(exception.getMessage, fault, exception)
    }
  }

  implicit private def zonedDateTimeToXMLGregorianCalendar(time: ZonedDateTime): XMLGregorianCalendar = {
    val gregorianCalendar = GregorianCalendar.from(time)

    val xmlGregorian = DatatypeFactory.newInstance().newXMLGregorianCalendar(gregorianCalendar)
    xmlGregorian.setTimezone(DatatypeConstants.FIELD_UNDEFINED)
    xmlGregorian
  }
}
