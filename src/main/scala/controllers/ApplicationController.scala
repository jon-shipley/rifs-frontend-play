package controllers

import javax.inject.Inject

import cats.data.OptionT
import cats.instances.future._
import forms._
import models._
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, Controller, Result}
import services.{ApplicationFormOps, ApplicationOps, OpportunityOps}

import scala.concurrent.{ExecutionContext, Future}

class ApplicationController @Inject()(applications: ApplicationOps, applicationForms: ApplicationFormOps, opportunities: OpportunityOps)(implicit ec: ExecutionContext)
  extends Controller {

  def showOrCreateForForm(id: ApplicationFormId) = Action.async {
    applications.getOrCreateForForm(id).map {
      case Some(app) => Redirect(controllers.routes.ApplicationController.show(app.id))
      case None => NotFound
    }
  }

  def show(id: ApplicationId) = Action.async {
    val t = for {
      a <- OptionT(applications.overview(id))
      af <- OptionT(applicationForms.byId(a.applicationFormId))
      opp <- OptionT(opportunities.byId(af.opportunityId))
    } yield (af, a, opp)

    t.value.map {
      case Some((form, overview, opp)) => Ok(views.html.showApplicationForm(form, overview, opp))
      case None => NotFound
    }
  }

  import ApplicationData._

  def showSectionForm(id: ApplicationId, sectionNumber: Int) = Action.async { request =>
    fieldsFor(sectionNumber) match {
      case Some(fields) =>
        applications.getSection(id, sectionNumber).flatMap { section =>
          val doValidation = request.flash.get("doValidation").exists(_ => true)
          val doPreviewValidation = request.flash.get("doPreviewValidation").exists(_ => true)

          val errs: FieldErrors = section.map { s =>
            if (doValidation) check(s.answers, checksFor(sectionNumber))
            else if (doPreviewValidation) check(s.answers, previewChecksFor(sectionNumber))
            else noErrors
          }.getOrElse(noErrors)

          val hints = section.map(s => hinting(s.answers, checksFor(sectionNumber))).getOrElse(List())
          Logger.debug(hints.toString)

          renderSectionForm(id, sectionNumber, section, questionsFor(sectionNumber), fields, errs, hints)
        }

      // Temporary hack to display the WIP page for sections that we haven't yet coded up
      case None => Future.successful(Ok(views.html.wip(routes.ApplicationController.show(id).url)))
    }
  }

  def renderSectionForm(id: ApplicationId, sectionNumber: Int, section: Option[ApplicationSection], questions: Map[String, Question], fields: Seq[Field], errs: FieldErrors, hints: FieldHints) = {
    val ft = for {
      a <- OptionT(applications.byId(id))
      af <- OptionT(applicationForms.byId(a.applicationFormId))
      o <- OptionT(opportunities.byId(af.opportunityId))
      ov <- OptionT(applications.overview(id))
    } yield (a, af, o, ov)

    ft.value.map {
      case Some((app, appForm, opp, overview)) =>
        val formSection: ApplicationFormSection = appForm.sections.find(_.sectionNumber == sectionNumber).get
        val answers = section.map { s => JsonHelpers.flatten("", s.answers) }.getOrElse(Map[String, String]())
        Ok(views.html.sectionForm(app, overview, appForm, section, formSection, opp, fields, questions, answers, errs, hints))
      case None => NotFound
    }
  }


  import JsonHelpers._

  /**
    * Note if more than one button action name is present in the keys then it is indeterminate as to
    * which one will be returned. This shouldn't occur if the form is properly submitted from a
    * browser, though.
    */
  def decodeButton(keys: Set[String]): Option[ButtonAction] = keys.flatMap(ButtonAction.unapply).headOption.map {
    case Save => if (keys.contains("_complete_checkbox")) Complete else Save
    case b => b
  }

  def postSection(id: ApplicationId, sectionNumber: Int) = Action.async(parse.urlFormEncoded) { implicit request =>
    Logger.debug(request.body.toString())
    // Drop keys that start with '_' as these are "system" keys like the button name
    val jsonFormValues = formToJson(request.body.filterKeys(k => !k.startsWith("_")))
    val button: Option[ButtonAction] = decodeButton(request.body.keySet)

    takeAction(id, sectionNumber, button, jsonFormValues)
  }

  def takeAction(id: ApplicationId, sectionNumber: Int, button: Option[ButtonAction], fieldValues: JsObject): Future[Result] = {
    button.map {
      case Complete =>
        val errs = check(fieldValues, checksFor(sectionNumber))
        if (errs.isEmpty) {
          applications.completeSection(id, sectionNumber, fieldValues).map { _ =>
            Redirect(routes.ApplicationController.show(id))
          }
        } else {
          applications.saveSection(id, sectionNumber, fieldValues).map { _ =>
            Redirect(routes.ApplicationController.showSectionForm(id, sectionNumber)).flashing(("doValidation", "true"))
          }
        }
      case Save =>
        applications.saveSection(id, sectionNumber, fieldValues).map { _ =>
          Redirect(routes.ApplicationController.show(id))
        }
      case Preview =>
        applications.saveSection(id, sectionNumber, fieldValues).map { _ =>
          val errs = check(fieldValues, previewChecksFor(sectionNumber))
          if (errs.isEmpty) {
            Redirect(routes.ApplicationPreviewController.previewSection(id, sectionNumber))
          } else {
            Redirect(routes.ApplicationController.showSectionForm(id, sectionNumber)).flashing(("doPreviewValidation", "true"))
          }
        }
    }.getOrElse(Future.successful(BadRequest))
  }

  def selectPreviewRules(rules: Map[String, Seq[FieldRule]]): Map[String, Seq[FieldRule]] = {
    rules.map { case (n, rs) => n -> rs.filter(_.validateOnPreview) }
  }

  def check(fieldValues: JsObject, checks: Map[String, FieldCheck]): FieldErrors = {
    val errs = checks.toList.flatMap {
      case (fieldName, check) =>
        fieldValues \ fieldName match {
          case JsDefined(jv) => check(fieldName, jv)
          case _ => check(fieldName, JsNull)
        }
    }
    Logger.debug(errs.toString)
    errs
  }

  def hinting(fieldValues: JsObject, checks: Map[String, FieldCheck]): FieldHints = {
    Logger.debug(s"Hinting $fieldValues with $checks")
    checks.toList.flatMap {
      case (fieldName, check) =>
        fieldValues \ fieldName match {
          case JsDefined(jv) => check.hint(fieldName, jv)
          case _ => check.hint(fieldName, JsNull)
        }
    }
  }
}
