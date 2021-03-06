package forms.validation

import cats.data.ValidatedNel
import cats.syntax.validated._
import play.api.libs.json.JsValue

case class WordCountValidator(maxWords: Int) extends FieldValidator[String, String] {

  import WordCountValidator._

  override def normalise(s: String): String = s.trim()

  override def doValidation(path: String, s: Normalised[String]): ValidatedNel[FieldError, String] = {
    normalise(s) match {
      case n if n.split("\\s+").length > maxWords => FieldError(path, "Word limit exceeded").invalidNel
      case n => n.validNel
    }
  }

  override def hintText(path: String, jv: JsValue): List[FieldHint] = {
    val wordCount = normalise(jv.validate[String].asOpt.getOrElse("")).split("\\s+").filterNot(_ == "").length

    val text = wordCount match {
      case 0 => noWords(maxWords)
      case _ if wordCount > maxWords => overLimit(wordCount - maxWords)
      case _ => wordsRemaining(maxWords - wordCount)
    }

    List(FieldHint(path, text, Some("WordCount"), Some(s"""{\"maxWords\": $maxWords}""")))
  }
}

object WordCountValidator {
  def ws(n: Int) = if (n == 1) "word" else "words"

  def wordsRemaining(wordsLeft: Int) = s"Words remaining: $wordsLeft"

  def overLimit(over: Int) = s"$over ${ws(over)} over limit"

  def noWords(max: Int) = s"$max ${ws(max)} maximum"
}