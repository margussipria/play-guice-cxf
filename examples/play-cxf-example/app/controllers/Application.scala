package controllers

import com.google.inject.Inject
import play.api.mvc._
import services.Greeter

class Application @Inject() (
  service: Greeter,
  controllerComponents: ControllerComponents
) extends AbstractController(controllerComponents) {

  def index: Action[AnyContent] = Action {
    Ok("Your new application is ready.")
  }

  def hi: Action[AnyContent] = Action {
    Ok(service.sayHi())
  }
}
