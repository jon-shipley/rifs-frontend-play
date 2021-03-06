package controllers

import forms.validation.{FieldError, FieldHint}
import play.api.Logger
import play.api.libs.json.{JsDefined, JsNull, JsObject, JsValue}


object FieldCheckHelpers {
  type FieldErrors = List[FieldError]
  val noErrors: FieldErrors = List()
  type FieldHints = List[FieldHint]

  def check(fieldValues: JsObject, checks: Map[String, FieldCheck]): FieldErrors = {
    checkList(fieldValues, checks).flatMap { case (n, v, c) => c(n, v) }
  }

  def hinting(fieldValues: JsObject, checks: Map[String, FieldCheck]): FieldHints = {
    checkList(fieldValues, checks).flatMap { case (n, v, c) => c.hint(n, v) }
  }

  def checkList(fieldValues: JsObject, checks: Map[String, FieldCheck]): List[(String, JsValue, FieldCheck)] = {
    checks.toList.map {
      case ("", check) => ("", fieldValues, check)
      case (fieldName, check) =>
        fieldValues \ fieldName match {
          case JsDefined(jv) => (fieldName, jv, check)
          case _ => (fieldName, JsNull, check)
        }
    }
  }
}
