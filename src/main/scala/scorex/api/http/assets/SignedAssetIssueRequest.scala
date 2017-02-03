package scorex.api.http.assets

import com.google.common.base.Charsets
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import play.api.libs.json.{JsPath, Reads}
import scorex.account.PublicKeyAccount
import scorex.api.http.formats.SignatureReads
import scorex.crypto.encode.Base58
import scorex.transaction.assets.IssueTransaction
import play.api.libs.functional.syntax._

import scala.util.Try
import scorex.api.http.formats._
import scorex.transaction.ValidationError
import scorex.transaction.ValidationError.InvalidSignature

@ApiModel(value = "Signed Asset issue transaction")
case class SignedAssetIssueRequest(@ApiModelProperty(value = "Base58 encoded Issuer public key", required = true)
                                   sender: PublicKeyAccount,
                                   @ApiModelProperty(value = "Base58 encoded name of Asset", required = true)
                                   name: String,
                                   @ApiModelProperty(value = "Base58 encoded description of Asset", required = true)
                                   description: String,
                                   @ApiModelProperty(required = true, example = "1000000")
                                   quantity: Long,
                                   @ApiModelProperty(allowableValues = "range[0,8]", example = "8", dataType = "integer", required = true)
                                   decimals: Byte,
                                   @ApiModelProperty(required = true)
                                   reissuable: Boolean,
                                   @ApiModelProperty(required = true)
                                   fee: Long,
                                   @ApiModelProperty(required = true)
                                   timestamp: Long,
                                   @ApiModelProperty(required = true)
                                   signature: String) {

  def toTx: Either[ValidationError, IssueTransaction] =
    Base58.decode(signature).toEither.left.map(_ => InvalidSignature).flatMap { signature =>
      IssueTransaction.create(
        sender,
        name.getBytes(Charsets.UTF_8),
        description.getBytes(Charsets.UTF_8),
        quantity,
        decimals,
        reissuable,
        fee,
        timestamp,
        signature)
    }
}

object SignedAssetIssueRequest {

  implicit val assetIssueRequestReads: Reads[SignedAssetIssueRequest] = (
    (JsPath \ "senderPublicKey").read[PublicKeyAccount] and
      (JsPath \ "name").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "quantity").read[Long] and
      (JsPath \ "decimals").read[Byte] and
      (JsPath \ "reissuable").read[Boolean] and
      (JsPath \ "fee").read[Long] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "signature").read[String](SignatureReads)
    ) (SignedAssetIssueRequest.apply _)

}
