package controllers

import forms.validation._
import play.api.libs.json._

trait FieldCheck {
  def apply(path: String, value: JsValue): List[FieldError]

  def hint(path: String, value: JsValue): Option[FieldHint]
}

object FieldChecks {
  val mandatoryCheck = new FieldCheck {
    override def apply(path: String, value: JsValue): List[FieldError] =
      MandatoryValidator.validate(path, value.validate[String].asOpt).fold(_.toList, _ => List())

    override def hint(path: String, value: JsValue): Option[FieldHint] = None
  }

  def mandatoryText(wordLimit: Int) = new FieldCheck {
    val validator = MandatoryValidator.andThen(WordCountValidator(wordLimit))

    override def apply(path: String, value: JsValue): List[FieldError] = validator.validate(path, decodeString(value)).fold(_.toList, _ => List())

    override def hint(path: String, value: JsValue): Option[FieldHint] = validator.hintText(path, value.validate[String].asOpt)
  }

  def fromValidator[T: Reads](v: FieldValidator[T, _]): FieldCheck = new FieldCheck {
    override def apply(path: String, jv: JsValue) = jv.validate[T].map { x =>
      v.validate(path, x).fold(_.toList, _ => List())
    } match {
      case JsSuccess(msgs, _) => msgs
      case JsError(errs) => List(FieldError(path, "Could not decode form values!"))
    }

    override def hint(path: String, jv: JsValue): Option[FieldHint] = v.hintText(path, jv.validate[String].asOpt)
  }



  def intFieldCheck(min:Int, max:Int) = new FieldCheck {
    val validator: FieldValidator[Option[String], Int] = MandatoryValidator.andThen(IntValidator(min, max))

    override def apply(path: String, jv: JsValue) = validator.validate(path, decodeString(jv)).fold(_.toList, _ => List())

    override def hint(path: String, value: JsValue): Option[FieldHint] = validator.hintText(path, value.validate[String].asOpt)
  }

  def decodeString(jv: JsValue): Option[String] = {
    jv match {
      case JsString(s) => Some(s)
      case _ => None
    }
  }
}