package org.apache.cxf

import org.apache.cxf.jaxws.JaxWsProxyFactoryBean

import scala.collection.Map

trait ClientSetupHooks {

  def preConfiguration(value: JaxWsProxyFactoryBean): Unit = ()

  def getProperties: Map[String, AnyRef] = Map.empty

  def postConfiguration(value: JaxWsProxyFactoryBean): Unit = ()

  def postCreate(value: AnyRef): Unit = ()
}
