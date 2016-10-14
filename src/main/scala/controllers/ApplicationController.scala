package controllers

import javax.inject.Inject

import cats.data.OptionT
import cats.instances.future._
import models.ApplicationId
import play.api.Logger
import play.api.mvc.{Action, Controller}
import services.{ApplicationOps, OpportunityOps}

import scala.concurrent.ExecutionContext

class ApplicationController @Inject()(applications: ApplicationOps, opportunities: OpportunityOps)(implicit ec: ExecutionContext) extends Controller {

  def show(id: ApplicationId) = Action.async {
    applications.byId(id).map {
      case Some(application) => Ok(views.html.showApplication(application))
      case None => NotFound
    }
  }

  def sectionForm(id:ApplicationId, sectionNumber:Int) = {
    if (sectionNumber == 1) title(id)
    else Action { Ok(views.html.wip())}
  }

  def title(id: ApplicationId) = Action.async {
    val ft = for {
      a <- OptionT(applications.byId(id))
      o <- OptionT(opportunities.byId(a.opportunityId))
    } yield (a, o)

    ft.value.map {
      case Some((app, opp)) => Ok(views.html.titleForm(app, app.sections.find(_.sectionNumber == 1).get, opp))
      case None => NotFound
    }
  }

  /**
    * Note if more than one button action name is present in the keys then it is indeterminate as to
    * which one will be returned.
    */
  def decodeAction(keys: Set[String]): Option[ButtonAction] = keys.flatMap(ButtonAction.unapply).headOption

  def section(id: ApplicationId, sectionNumber: Int) = Action(parse.urlFormEncoded) { implicit request =>
    Logger.debug(request.body.toString)

    val buttonAction: Option[ButtonAction] = decodeAction(request.body.keySet)
    Logger.debug(s"Button action is $buttonAction")
    buttonAction.map {
      case Complete => Redirect(routes.ApplicationController.show(id))
      case Save => Redirect(routes.ApplicationController.show(id))
      case Preview => Redirect(routes.ApplicationController.show(id))
    }.getOrElse(BadRequest)
  }
}
