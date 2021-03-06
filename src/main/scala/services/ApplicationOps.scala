package services

import com.google.inject.ImplementedBy
import controllers.FieldCheckHelpers.FieldErrors
import models._
import play.api.libs.json.{JsObject, Reads}

import scala.concurrent.Future

@ImplementedBy(classOf[ApplicationService])
trait ApplicationOps {

  def byId(id: ApplicationId): Future[Option[Application]]

  def getOrCreateForForm(applicationFormId: ApplicationFormId): Future[Option[Application]]

  def overview(id: ApplicationId): Future[Option[ApplicationOverview]]

  def detail(id: ApplicationId): Future[Option[ApplicationDetail]]

  def sectionDetail(id: ApplicationId, sectionNum:Int): Future[Option[ApplicationSectionDetail]]

  def reset(): Future[Unit]

  def saveSection(id: ApplicationId, sectionNumber: Int, doc: JsObject): Future[Unit]

  def completeSection(id: ApplicationId, sectionNumber: Int, doc: JsObject): Future[FieldErrors]

  def saveItem(id: ApplicationId, sectionNumber: Int, doc: JsObject): Future[FieldErrors]

  def getItem[T: Reads](id: ApplicationId, sectionNumber: Int, itemNumber: Int): Future[Option[T]]

  def deleteItem(id: ApplicationId, sectionNumber: Int, itemNumber: Int): Future[Unit]

  def getSection(id: ApplicationId, sectionNumber: Int): Future[Option[ApplicationSection]]

  def getSections(id: ApplicationId): Future[Seq[ApplicationSection]]

  def deleteSection(id: ApplicationId, sectionNumber: Int): Future[Unit]

  def clearSectionCompletedDate(id: ApplicationId, sectionNumber: Int): Future[Unit]

  def submit(id: ApplicationId): Future[Option[SubmittedApplicationRef]]

  def updatePersonalReference(id: ApplicationId, reference: String): Future[Unit]
}
