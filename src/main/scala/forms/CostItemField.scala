package forms

import controllers.{FieldCheck, FieldChecks, JsonHelpers}
import forms.validation.{CostItemValidator, CostItemValues, FieldError, FieldHint}
import models._
import play.api.libs.json.{JsObject, Json}
import play.twirl.api.Html

case class CostItemField(name: String) extends Field {
  implicit val civReads = Json.reads[CostItemValues]

  val itemNameField = TextField(Some("Item"), s"$name.itemName", isNumeric = false, 20)
  val costField = CurrencyField(Some("Cost"), s"$name.cost", None)
  val justificationField = TextAreaField(Some("Justification of item"), s"$name.justification", 500)

  override def check: FieldCheck = FieldChecks.fromValidator(CostItemValidator)

  override def renderFormInput(questions: Map[String, Question], answers: JsObject, errs: Seq[FieldError], hints: Seq[FieldHint]) =
    views.html.renderers.costItemField(this, questions, JsonHelpers.flatten(answers), errs, hints)

  override def renderPreview(questions: Map[String, Question], answers: JsObject) = Html("")
}
