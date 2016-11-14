package controllers

import javax.inject.Inject

import models._
import forms.validation.{FieldError, FieldHint, SectionError}
import play.api.mvc.{Action, Controller}
import services.ApplicationOps

import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject()(actionHandler: ActionHandler, applications: ApplicationOps)(implicit ec: ExecutionContext)
  extends Controller with ApplicationResults {

  def showOrCreateForForm(id: ApplicationFormId) = Action.async {
    applications.getOrCreateForForm(id).map {
      case Some(app) => redirectToOverview(app.id)
      case None => NotFound
    }
  }

  def show(id: ApplicationId) = Action.async {
    actionHandler.gatherApplicationDetails(id).map {
      case Some((overview, form, opp)) => Ok(views.html.showApplicationForm(form, overview, opp, List()))
      case None => NotFound
    }
  }

  def reset = Action.async {
    applications.deleteAll().map(_ => Redirect(controllers.routes.StartPageController.startPage()))
  }

  import ApplicationData._
  import FieldCheckHelpers._

  def showSectionForm(id: ApplicationId, sectionNumber: Int) = Action.async { request =>
    fieldsFor(sectionNumber) match {
      case Some(fields) =>
        applications.getSection(id, sectionNumber).flatMap { section =>
          val hints = section.map(s => hinting(s.answers, checksFor(sectionNumber))).getOrElse(List())
          actionHandler.renderSectionForm(id, sectionNumber, section, questionsFor(sectionNumber), fields, noErrors, hints)
        }

      // Temporary hack to display the WIP page for sections that we haven't yet coded up
      case None => Future.successful(wip(routes.ApplicationController.show(id).url))
    }
  }

  def postSection(id: ApplicationId, sectionNumber: Int) = Action.async(JsonForm.parser) { implicit request =>
    request.body.action match {
      case Complete => actionHandler.doComplete(id, sectionNumber, request.body.values)
      case Save => actionHandler.doSave(id, sectionNumber, request.body.values)
      case SaveItem => actionHandler.doSaveItem(id, sectionNumber, request.body.values)
      case Preview => actionHandler.doPreview(id, sectionNumber, request.body.values)
    }
  }

  def submit(id: ApplicationId) = Action.async { request =>
    actionHandler.gatherApplicationDetails(id).map {
      case Some((overview, form, opp)) =>
        val sectionErrors: Seq[SectionError] = form.sections.sortBy(_.sectionNumber).flatMap { fs =>
          overview.sections.find(_.sectionNumber == fs.sectionNumber) match {
            case None => Some(SectionError(fs, "Not started"))
            case Some(s) => checkSection(fs, s) 
          }
        }
        Ok(views.html.showApplicationForm(form, overview, opp, sectionErrors))
      case None => NotFound
    }
  }

  def checkSection(fs:ApplicationFormSection, s: ApplicationSectionOverview): Option[SectionError] = {
    s.completedAt match {
      case Some(_) => None
      case None => Some(SectionError(fs, "In progress"))
    }
  }

  def getStatus(overview: ApplicationOverview, secno: Int) = {
    val y = overview.sections.find(_.sectionNumber == secno).map(s => s.status).getOrElse("Not Started")
    y
  }

}
