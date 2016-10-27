package controllers

import cats.data.NonEmptyList
import forms._
import forms.validation._
import play.api.libs.json._

object ApplicationData {

  type FieldErrors = Map[String, NonEmptyList[String]]
  val noErrors: FieldErrors = Map()

  type FieldCheck = JsValue => List[String]

  def fromValidator[T: Reads](v: FieldValidator[T, _]): FieldCheck = { jv =>
    jv.validate[T].map { x =>
      v.validate(x).fold(_.toList, _ => List())
    } match {
      case JsSuccess(msgs, path) => msgs
      case JsError(errs) => List("Could not decode form values!")
    }
  }

  def decodeString(jv: JsValue): Option[String] = {
    jv match {
      case JsString(s) => Some(s)
      case _ => None
    }
  }

  val titleValidator: FieldValidator[Option[String], String] = MandatoryValidator.andThen(WordCountValidator(20))

  val titleCheck: FieldCheck = (jv) => titleValidator.validate(decodeString(jv)).fold(_.toList, _ => List())

  val titleFormChecks: Map[String, FieldCheck] = Map("title" -> titleCheck)

  implicit val dvReads = Json.reads[DateValues]
  implicit val dwdReads = Json.reads[DateWithDaysValues]
  val daysCheck: FieldCheck = (jv) => MandatoryValidator.andThen(IntValidator(1, 9)).validate(decodeString(jv)).fold(_.toList, _ => List())

  val dateFormChecks: Map[String, FieldCheck] = Map {
    "provisionalDate" -> fromValidator(DateWithDaysValidator(allowPast = false, 1, 9))
  }


  val titleFormRules: Map[String, Seq[FieldRule]] = Map("title" -> Seq(WordCountRule(20), MandatoryRule()))

  val dateFormRules: Map[String, Seq[FieldRule]] = Map(
    "date" -> Seq(DateRule(allowPast = false)),
    "days" -> Seq(MandatoryRule(), IntRule(1, 9)))

  def rulesFor(sectionNumber: Int): Map[String, Seq[FieldRule]] = {
    sectionNumber match {
      case 1 => titleFormRules
      case 2 => dateFormRules
      case _ => Map()
    }
  }

  def checksFor(sectionNumber: Int): Map[String, FieldCheck] = {
    sectionNumber match {
      case 1 => titleFormChecks
      case 2 => dateFormChecks
      case _ => Map()
    }
  }

  val titleFormQuestions = Map("title" -> "What is your event called?")
  val dateFormQuestions = Map(
    "days" -> "How long will it last?",
    "date" -> "When do you propose to hold the event?"
  )

  def questionsFor(sectionNumber: Int): Map[String, String] = {
    sectionNumber match {
      case 1 => titleFormQuestions
      case 2 => dateFormQuestions
      case _ => Map()
    }
  }

  val titleFormFields: Seq[Field] = Seq(TextField(None, "title", titleFormRules.getOrElse("title", Seq())))
  val dateFormFields: Seq[Field] = Seq(DateWithDaysField("provisionalDate", DateField("date"), TextField(None, "days")))

  def fieldsFor(sectionNum: Int): Option[Seq[Field]] = {
    sectionNum match {
      case 1 => Some(titleFormFields)
      case 2 => Some(dateFormFields)
      case _ => None
    }
  }
}
