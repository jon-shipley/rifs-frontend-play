package forms

import controllers.{FieldCheck, FieldChecks, JsonHelpers}
import forms.validation.{CurrencyValidator, FieldError, FieldHint}
import models._
import play.api.libs.json._
import play.twirl.api.Html

case class CurrencyField(label: Option[String], name: String, validator: Option[CurrencyValidator]) extends Field {
  implicit val osReads = new Reads[Option[String]] {
    override def reads(json: JsValue): JsResult[Option[String]] =
      json match {
        case JsNull => JsSuccess(None)
        case js => js.validate[JsString].map(js => Option(js.value))
      }
  }

  override val check: FieldCheck = FieldChecks.fromValidator(validator.getOrElse(CurrencyValidator.anyValue))

  override def renderFormInput(questions: Map[String, Question], answers: JsObject, errs: Seq[FieldError], hints: Seq[FieldHint]) =
    views.html.renderers.currencyField(this, questions, JsonHelpers.flatten(answers), errs, hints)

  override def renderPreview(questions: Map[String, Question], answers: JsObject) =
    views.html.renderers.preview.currencyField(this, JsonHelpers.flatten(answers))
}
