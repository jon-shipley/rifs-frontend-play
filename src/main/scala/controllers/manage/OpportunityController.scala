package controllers.manage

import javax.inject.Inject

import actions.OpportunityAction
import cats.data.Validated._
import controllers._
import forms.validation.DateTimeRangeValues
import forms.{DateTimeRangeField, DateValues, TextAreaField, TextField}
import models._
import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.mvc.{Action, Controller, Result}
import services.OpportunityOps

import scala.concurrent.{ExecutionContext, Future}


class OpportunityController @Inject()(opportunities: OpportunityOps, OpportunityAction: OpportunityAction)(implicit ec: ExecutionContext) extends Controller {

  def showOpportunityPreview(id: OpportunityId, sectionNumber: Option[Int]) = OpportunityAction(id) { implicit request =>
    Ok(views.html.manage.opportunityPreview(request.uri, request.opportunity, sectionNumber.getOrElse(1)))
  }

  def showNewOpportunityForm() = Action { request =>
    Ok(views.html.manage.newOpportunityChoice(request.uri))
  }

  def chooseHowToCreateOpportunity(choiceText: Option[String]) = Action { implicit request =>
    CreateOpportunityChoice(choiceText).map {
      case NewOpportunityChoice => Ok(views.html.wip(routes.OpportunityController.showNewOpportunityForm().url))
      case ReuseOpportunityChoice => Redirect(controllers.manage.routes.OpportunityController.showOpportunityLibrary())
    }.getOrElse(Redirect(controllers.manage.routes.OpportunityController.showNewOpportunityForm()))
  }

  def showOpportunityLibrary = Action.async {
    opportunities.getOpenOpportunitySummaries.map { os => Ok(views.html.manage.showOpportunityLibrary(os)) }
  }

  val deadlinesField = DateTimeRangeField("deadlines", allowPast = false, isEndDateMandatory = false)
  val deadlineQuestions = Map(
    "deadlines.startDate" -> Question("When does the opportunity open?"),
    "deadlines.endDate" -> Question("What is the closing date?")
  )

  val titleField = TextField(label = Some("title"), name = "title", isNumeric = false, maxWords = 20)
  val titleQuestion = Map("title" -> Question("What is your opportunity called ?"))

  def editTitle(id: OpportunityId) = OpportunityAction(id) { request =>
    val answers = JsObject(Seq("title" -> Json.toJson(request.opportunity.title)))
    Ok(views.html.manage.editTitleForm(titleField, request.opportunity, titleQuestion, answers, Seq(), Seq()))
  }

  def saveTitle(id: OpportunityId) = OpportunityAction(id).async(JsonForm.parser) { implicit request =>
    saveTextField(request.opportunity, JsonHelpers.flatten(request.body.values), "title", request.body.values)
  }

  //Refactor - move this to a service or action handler ?
  def saveTextField(opp: Opportunity, answers: Map[String, String], sectionFieldName: String, fieldValues: JsObject): Future[Result] = {
    answers match {
      case _ => titleField.check(titleField.name, Json.toJson(answers.getOrElse(sectionFieldName, ""))) match {
        case Nil => opportunities.saveSummary(opp.summary.copy(title = answers.getOrElse(sectionFieldName, ""))).map(_ => Ok(views.html.wip("")))
        case errs => Future.successful(Ok(views.html.manage.editTitleForm(titleField, opp, titleQuestion, fieldValues, errs, Seq())))
      }
    }
  }

  val DESCRIPTION = "description"
  val descriptionField = TextAreaField(Some(DESCRIPTION), DESCRIPTION, 501)
  val descriptionQuestions = Map (DESCRIPTION ->
    Question("Be as specific as possible so that applicants fully understand the aim of the opportunity." +
      " This will help ensure that applications meet the criteria and objectives.")
  )

  def editDescription(id: OpportunityId) = OpportunityAction(id) { request =>
    request.opportunity.description.find(_.sectionNumber == OpportunityDefs.ABOUT_SECTION_NO ) match {
      case Some(sect) =>
        val answers = JsObject (Seq (DESCRIPTION -> Json.toJson(sect.text) ) )
        val hints = FieldCheckHelpers.hinting(answers, Map(DESCRIPTION -> descriptionField.check))
        Ok(views.html.manage.editDescriptionForm(descriptionField, request.opportunity,
          routes.OpportunityController.editDescription(id).url,
          descriptionQuestions, answers, Seq (), hints ) )
      case None => NotFound
    }
  }

  def saveDescription(id: OpportunityId) = OpportunityAction(id).async(JsonForm.parser) { implicit request =>
    // TODO: check the value of request.body.action
    (request.body.values \ DESCRIPTION).toOption.map { fValue =>
      descriptionField.check(DESCRIPTION, fValue) match {
        case Nil =>
          Future {
            opportunities.saveDescriptionSectionText(id, OpportunityDefs.ABOUT_SECTION_NO, Some(fValue.as[String]))
          }.map(_ => Redirect(controllers.routes.OpportunityController.showOverviewPage(id)))
        case errors =>
          val hints = FieldCheckHelpers.hinting(request.body.values, Map(DESCRIPTION -> descriptionField.check))
          Future.successful(Ok(views.html.manage.editDescriptionForm(descriptionField, request.opportunity,
            routes.OpportunityController.editDescription(id).url,
            descriptionQuestions, request.body.values, errors, hints)))
      }
    }.getOrElse( Future.successful(BadRequest) )
  }

  def duplicate(opportunityId: OpportunityId) = OpportunityAction(opportunityId) { request =>
    Ok(views.html.wip(controllers.routes.OpportunityController.showOverviewPage(opportunityId).url))
  }

  def viewTitle(id: OpportunityId) = OpportunityAction(id) { request =>
    Ok(views.html.manage.viewTitle(request.opportunity))
  }

  def viewDescription(id: OpportunityId) = OpportunityAction(id) { request =>
    Ok(views.html.manage.viewDescription(request.opportunity))
  }
  def viewGrantValue(id: OpportunityId) = OpportunityAction(id) { request =>
    Ok(views.html.manage.viewGrantValue(request.opportunity))
  }

  def viewOppSection(id: OpportunityId, sectionNum: Int) = OpportunityAction(id) { request =>
    Ok(views.html.manage.viewOppSection(request.opportunity, sectionNum))
  }

  def showPMGuidancePage(backUrl: String) = Action { request =>
    Ok(views.html.manage.guidance(backUrl))
  }

  implicit val dvFmt = Json.format[DateValues]
  implicit val dtrFmt = Json.format[DateTimeRangeValues]

  def viewDeadlines(id: OpportunityId) = OpportunityAction(id) { request =>
    val answers = JsObject(Seq("deadlines" -> Json.toJson(dateTimeRangeValuesFor(request.opportunity))))
    Ok(views.html.manage.viewDeadlines(deadlinesField, request.opportunity, deadlineQuestions, answers))
  }

  def editDeadlines(id: OpportunityId) = OpportunityAction(id) { request =>
    val answers = JsObject(Seq("deadlines" -> Json.toJson(dateTimeRangeValuesFor(request.opportunity))))
    Ok(views.html.manage.editDeadlinesForm(deadlinesField, request.opportunity, deadlineQuestions, answers, Seq(), Seq()))
  }

  private def dateTimeRangeValuesFor(opp: Opportunity) = {
    val sdv = dateValuesFor(opp.startDate)
    val edv = opp.endDate.map(dateValuesFor)
    DateTimeRangeValues(Some(sdv), edv, edv.map(_ => "yes").orElse(Some("no")))
  }

  private def dateValuesFor(ld: LocalDate) =
    DateValues(Some(ld.getDayOfMonth.toString), Some(ld.getMonthOfYear.toString), Some(ld.getYear.toString))

  def saveDeadlines(id: OpportunityId) = OpportunityAction(id).async(JsonForm.parser) { implicit request =>
    (request.body.values \ "deadlines").validate[DateTimeRangeValues] match {
      case JsSuccess(vs, _) =>
        deadlinesField.validator.validate(deadlinesField.name, vs) match {
          case Valid(v) =>
            val summary = request.opportunity.summary.copy(startDate = v.startDate, endDate = v.endDate)
            opportunities.saveSummary(summary).map(_ => Redirect(controllers.routes.OpportunityController.showOverviewPage(id)))
          case Invalid(errors) =>
            Future.successful(Ok(views.html.manage.editDeadlinesForm(deadlinesField, request.opportunity, deadlineQuestions, request.body.values, errors.toList, Seq())))
        }
      case JsError(errors) => Future.successful(BadRequest(errors.toString))
    }
  }


}

sealed trait CreateOpportunityChoice {
  def name: String
}

object CreateOpportunityChoice {
  def apply(s: Option[String]): Option[CreateOpportunityChoice] = s match {
    case Some(NewOpportunityChoice.name) => Some(NewOpportunityChoice)
    case Some(ReuseOpportunityChoice.name) => Some(ReuseOpportunityChoice)
    case _ => None
  }
}

case object NewOpportunityChoice extends CreateOpportunityChoice {
  val name = "new"
}

case object ReuseOpportunityChoice extends CreateOpportunityChoice {
  val name = "reuse"
}