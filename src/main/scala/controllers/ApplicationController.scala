package controllers

import javax.inject.Inject

import cats.data.OptionT
import cats.instances.future._
import models.ApplicationFormId
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, Controller, Result}
import services.{ApplicationFormOps, ApplicationOps, OpportunityOps}

import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject()(applications:ApplicationOps, applicationForms: ApplicationFormOps, opportunities: OpportunityOps)(implicit ec: ExecutionContext) extends Controller {

  def show(id: ApplicationFormId) = Action.async {
    val t = for {
      af <- OptionT(applicationForms.byId(id))
      a <- OptionT(applicationForms.overview(id))
    } yield (af, a)



    t.value.map {
      case Some((form, overview)) => Ok(views.html.showApplicationForm(form, overview))
      case None => NotFound
    }
  }

  def sectionForm(id: ApplicationFormId, sectionNumber: Int) = Action.async {
    if (sectionNumber == 1) applications.getSection(id, sectionNumber).flatMap { section => title(id, section.map(_.answers)) }
    else Future.successful(Ok(views.html.wip(routes.ApplicationController.show(id).url)))
  }

  def title(id: ApplicationFormId, formValues: Option[JsObject] = None) = {
    val ft = for {
      a <- OptionT(applicationForms.byId(id))
      o <- OptionT(opportunities.byId(a.opportunityId))
    } yield (a, o)

    ft.value.map {
      case Some((app, opp)) => Ok(views.html.titleForm(formValues.getOrElse(JsObject(Seq())), app, app.sections.find(_.sectionNumber == 1).get, opp))
      case None => NotFound
    }
  }

  /**
    * Note if more than one button action name is present in the keys then it is indeterminate as to
    * which one will be returned.
    */
  def decodeAction(keys: Set[String]): Option[ButtonAction] = keys.flatMap(ButtonAction.unapply).headOption

  def section(id: ApplicationFormId, sectionNumber: Int) = Action.async(parse.urlFormEncoded) { implicit request =>
    Logger.debug(request.body.toString)

    val buttonAction: Option[ButtonAction] = decodeAction(request.body.keySet)
    Logger.debug(s"Button action is $buttonAction")

    val jmap: Map[String, JsValue] = request.body.map {
      case (k, s :: Nil) => k -> JsString(s)
      case (k, ss) => k -> JsArray(ss.map(JsString))
    }

    takeAction(id, sectionNumber, buttonAction, JsObject(jmap))
  }

  def takeAction(id: ApplicationFormId, sectionNumber: Int, buttonAction: Option[ButtonAction], doc: JsObject): Future[Result] = {
    buttonAction.map {
      case Complete => Future.successful(Redirect(routes.ApplicationController.show(id)))
      case Save =>
        applications.saveSection(id, sectionNumber, doc).map { _ =>
          Redirect(routes.ApplicationController.show(id))
        }
      case Preview => Future.successful(Redirect(routes.ApplicationController.show(id)))
    }.getOrElse(Future.successful(BadRequest))
  }
}
