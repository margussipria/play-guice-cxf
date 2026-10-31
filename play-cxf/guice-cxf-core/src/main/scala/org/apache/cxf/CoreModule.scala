package org.apache.cxf

import com.google.inject.{Inject, Provider, Singleton}

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{AbstractModule, Key, Scopes}
import org.apache.cxf.binding.soap.{SoapBindingConfiguration, SoapVersion}
import org.apache.cxf.config.Configuration
import org.apache.cxf.transport.{DestinationFactory, DestinationFactoryManager}
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

abstract class CoreModule(val eagerly: Boolean = true) extends AbstractModule {
  import CoreModule._

  protected def maybeEagerly(scopedBindingBuilder: ScopedBindingBuilder): Unit ={
    if (eagerly) {
      scopedBindingBuilder.asEagerSingleton()
    } else {
      scopedBindingBuilder.in(Scopes.SINGLETON)
    }
  }

  def bindBus(addPlayStopHook: Boolean = false): Unit = {
    maybeEagerly {
      bind(classOf[Bus])
        .toProvider(classOf[CxfBusProvider])
    }
  }
}

object CoreModule {

  @Singleton
  private class CxfBusProvider extends Provider[Bus] {
    @Inject var injector: com.google.inject.Injector = _

    def get(): Bus = {
      val bus = BusFactory.newInstance().createBus()
      BusFactory.setDefaultBus(Option.empty[Bus].orNull)

      try {
        val clazz = Class.forName("play.api.inject.ApplicationLifecycle")

        Option(injector.getExistingBinding(Key.get(clazz))).foreach { lifecycle =>
          injector.getInstance(lifecycle.getKey).asInstanceOf[ApplicationLifecycle].addStopHook { () =>
            Future.successful(bus.shutdown(true))
          }
        }
      } catch { case _: ClassNotFoundException =>
      }

      bus
    }
  }

  def registerDestinationFactory(bus: Bus, transportFactory: DestinationFactory): Unit = {
    val destinationFactoryManager = bus.getExtension(classOf[DestinationFactoryManager])

    transportFactory.getTransportIds.asScala.foreach { transportIds =>
      destinationFactoryManager.registerDestinationFactory(transportIds, transportFactory)
    }
  }

  protected[cxf] def createBindingConfig(bindingConfig: Configuration): SoapBindingConfiguration = {
    val bindingConfiguration = new SoapBindingConfiguration

    bindingConfig.asOption[SoapVersion]("version").foreach(bindingConfiguration.setVersion)

    bindingConfiguration
  }
}
