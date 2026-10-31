package org.apache.cxf

import com.google.inject.{Inject, Provider, Singleton}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.cxf.config.Configuration
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.transport.http.HTTPTransportFactory

import scala.collection.{Seq, immutable}
import scala.reflect.{ClassTag, classTag}

abstract class ClientModule(eagerly: Boolean = true) extends CoreModule(eagerly) {
  import ClientModule._

  protected def bindHTTPTransport(): Unit = {
    bindBus()

    maybeEagerly {
      bind(classOf[HTTPTransportFactory])
        .toProvider(classOf[HTTPTransportFactoryProvider])
    }
  }

  protected def bindClient[T : ClassTag](
    key: String,
    hooks: Seq[Class[_ <: ClientSetupHooks]] = Seq.empty
  ): Unit = {
    bindHTTPTransport()

    maybeEagerly {
      bind(classTag[T].runtimeClass.asInstanceOf[Class[T]])
        .toProvider(new ClientProvider[T](key, hooks))
    }
  }
}

object ClientModule {
  final val ClientKeyConfig = "apache.cxf.client"

  @Singleton
  private class HTTPTransportFactoryProvider @Inject() (bus: Bus) extends Provider[HTTPTransportFactory] {

    def get(): HTTPTransportFactory = {
      val factory = new HTTPTransportFactory

      CoreModule.registerDestinationFactory(bus, factory)

      factory
    }
  }

  class ClientProvider[T : ClassTag](
    key: String,
    hooks: Seq[Class[_ <: ClientSetupHooks]] = Seq.empty
  ) extends com.google.inject.Provider[T] {
    @Inject var bus: Bus = _
    @Inject var injector: com.google.inject.Injector = _
    var config: Config = ConfigFactory.load()

    @Inject(optional = true)
    def setConfig(config: Config): Unit = {
      this.config = config
    }

    override def get(): T = {
      val config = this.config.getConfig(ClientKeyConfig)

      val initializedHooks = hooks.map(injector.getInstance(_))

      val factory = new JaxWsProxyFactoryBean()
      factory.setBus(bus)
      factory.setServiceClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])

      initializedHooks.foreach(_.preConfiguration(factory))

      val proxyProperties: java.util.Map[String, AnyRef] = Option(factory.getProperties).getOrElse {
        val proxyProperties = new java.util.HashMap[String, AnyRef]
        factory.setProperties(proxyProperties)
        proxyProperties
      }

      Configuration(config).asOption[Configuration](key).foreach { clientConfig =>

        clientConfig.asOption[String]("address").foreach(factory.setAddress)

        clientConfig.asOption[Configuration]("bindingConfig")
          .map(CoreModule.createBindingConfig)
          .foreach(factory.setBindingConfig)
      }

      initializedHooks
        .foldLeft(immutable.Map.empty[String, AnyRef]) { case (map, hookContainer) =>
          map ++ hookContainer.getProperties
        }
        .foreach { case (key, value) =>
          proxyProperties.put(key, value)
        }

      initializedHooks.foreach(_.postConfiguration(factory))

      val service = factory.create()

      initializedHooks.foreach(_.postCreate(service))

      service.asInstanceOf[T]
    }
  }
}
