package scorex.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import play.api.libs.json._
import scorex.account.Account
import scorex.app.Application
import scorex.crypto.EllipticCurveImpl
import scorex.network.Checkpoint
import scorex.network.Coordinator.BroadcastCheckpoint
import scorex.transaction.{BlockChain, SimpleTransactionModule}

import scala.util.Try

@Path("/blocks")
@Api(value = "/blocks")
case class BlocksApiRoute(application: Application) extends ApiRoute with CommonTransactionApiFunctions {

  val MaxBlocksPerRequest = 100
  implicit val transactionModule = application.transactionModule.asInstanceOf[SimpleTransactionModule]

  val settings = application.settings
  private val history = application.history
  private val coordinator = application.coordinator

  override lazy val route =
    pathPrefix("blocks") {
      signature ~ first ~ last ~ at ~ seq ~ height ~ heightEncoded ~ child ~ address ~ delay ~ checkpoint
    }

  @Path("/address/{address}/{from}/{to}")
  @ApiOperation(value = "Address", notes = "Get list of blocks generated by specified address", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "from", value = "Start block height", required = true, dataType = "integer", paramType = "path"),
    new ApiImplicitParam(name = "to", value = "End block height", required = true, dataType = "integer", paramType = "path"),
    new ApiImplicitParam(name = "address", value = "Wallet address ", required = true, dataType = "string", paramType = "path")
  ))
  def address: Route = {
    path("address" / Segment / IntNumber / IntNumber) { case (address, start, end) =>
      getJsonRoute {
        if (end >= 0 && start >= 0 && end - start >= 0 && end - start < MaxBlocksPerRequest) {
          val json = JsArray(history.generatedBy(new Account(address), start, end).map(_.json))
          JsonResponse(json, StatusCodes.OK)
        } else TooBigArrayAllocation.response
      }
    }
  }

  @Path("/child/{signature}")
  @ApiOperation(value = "Child", notes = "Get children of specified block", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "string", paramType = "path")
  ))
  def child: Route = {
    path("child" / Segment) { case encodedSignature =>
      getJsonRoute {
        withBlock(history, encodedSignature) { block =>
          history match {
            case blockchain: BlockChain =>
              blockchain.children(block).headOption.map(_.json).getOrElse(
                Json.obj("status" -> "error", "details" -> "No child blocks"))
            case _ =>
              Json.obj("status" -> "error", "details" -> "Not available for other option than linear blockchain")
          }
        }
      }
    }
  }

  @Path("/delay/{signature}/{blockNum}")
  @ApiOperation(value = "Average delay",
    notes = "Average delay in milliseconds between last $blockNum blocks starting from block with $signature", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "blockNum", value = "Number of blocks to count delay", required = true, dataType = "string", paramType = "path")
  ))
  def delay: Route = {
    path("delay" / Segment / IntNumber) { case (encodedSignature, count) =>
      getJsonRoute {
        withBlock(history, encodedSignature) { block =>
          history.averageDelay(block, count).map(d => Json.obj("delay" -> d))
            .getOrElse(Json.obj("status" -> "error", "details" -> "Internal error"))
        }
      }
    }
  }

  @Path("/height/{signature}")
  @ApiOperation(value = "Height", notes = "Get height of a block by its Base58-encoded signature", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "string", paramType = "path")
  ))
  def heightEncoded: Route = {
    path("height" / Segment) { case encodedSignature =>
      getJsonRoute {
        withBlock(history, encodedSignature) { block =>
          Json.obj("height" -> history.heightOf(block))
        }
      }
    }
  }

  @Path("/height")
  @ApiOperation(value = "Height", notes = "Get blockchain height", httpMethod = "GET")
  def height: Route = {
    path("height") {
      getJsonRoute {
        val json = Json.obj("height" -> history.height())
        JsonResponse(json, StatusCodes.OK)
      }
    }
  }

  @Path("/at/{height}")
  @ApiOperation(value = "At", notes = "Get block at specified height", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "height", value = "Block height", required = true, dataType = "integer", paramType = "path")
  ))
  def at: Route = {
    path("at" / IntNumber) { case height =>
      getJsonRoute {
        history match {
          case blockchain: BlockChain => {
            blockchain.blockAt(height).map(_.json) match {
              case Some(json) => JsonResponse(json + ("height" -> Json.toJson(height)), StatusCodes.OK)
              case None => {
                val json = Json.obj("status" -> "error", "details" -> "No block for this height")
                JsonResponse(json, StatusCodes.NotFound)
              }
            }
          }
          case _ =>
            val json =
              Json.obj("status" -> "error", "details" -> "Not available for other option than linear blockchain")
            JsonResponse(json, StatusCodes.NotFound)
        }
      }
    }
  }

  @Path("/seq/{from}/{to}")
  @ApiOperation(value = "Seq", notes = "Get block at specified heights", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "from", value = "Start block height", required = true, dataType = "integer", paramType = "path"),
    new ApiImplicitParam(name = "to", value = "End block height", required = true, dataType = "integer", paramType = "path")
  ))
  def seq: Route = {
    path("seq" / IntNumber / IntNumber) { case (start, end) =>
      getJsonRoute {
        history match {
          case blockchain: BlockChain =>
            if (end >= 0 && start >= 0 && end - start >= 0 && end - start < MaxBlocksPerRequest) {
              val json = JsArray(
                (start to end).map { height =>
                  blockchain.blockAt(height).map(_.json + ("height" -> Json.toJson(height)))
                    .getOrElse(Json.obj("error" -> s"No block at height $height"))
                })
              JsonResponse(json, StatusCodes.OK)
            } else TooBigArrayAllocation.response
          case _ =>
            JsonResponse(
              Json.obj("status" -> "error", "details" -> "Not available for other option than linear blockchain"),
              StatusCodes.BadRequest)
        }
      }
    }
  }


  @Path("/last")
  @ApiOperation(value = "Last", notes = "Get last block data", httpMethod = "GET")
  def last: Route = {
    path("last") {
      getJsonRoute {
        JsonResponse(history.lastBlock.json, StatusCodes.OK)
      }
    }
  }

  @Path("/first")
  @ApiOperation(value = "First", notes = "Get genesis block data", httpMethod = "GET")
  def first: Route = {
    path("first") {
      getJsonRoute {
        JsonResponse(history.genesis.json + ("height" -> Json.toJson(1)), StatusCodes.OK)
      }
    }
  }

  @Path("/signature/{signature}")
  @ApiOperation(value = "Signature", notes = "Get block by a specified Base58-encoded signature", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "signature", value = "Base58-encoded signature", required = true, dataType = "string", paramType = "path")
  ))
  def signature: Route = {
    path("signature" / Segment) { encodedSignature =>
      getJsonRoute {
        withBlock(history, encodedSignature) { block =>
          val height = history.heightOf(block.uniqueId).map(Json.toJson(_))
            .getOrElse(JsNull)
          block.json + ("height" -> height)
        }
      }
    }
  }

  @Path("/checkpoint")
  @ApiOperation(value = "Checkpoint", notes = "Broadcast checkpoint of blocks", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "message", value = "Checkpoint message", required = true, paramType = "body",
      dataType = "scorex.network.Checkpoint")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json with response or error")
  ))
  def checkpoint: Route = {
    def validateCheckpoint(checkpoint: Checkpoint): Option[ApiError] = {
      settings.checkpointPublicKey.map {publicKey =>
        if (!EllipticCurveImpl.verify(checkpoint.signature, checkpoint.toSign, publicKey)) Some(InvalidSignature)
        else None
      }.getOrElse(Some(InvalidMessage))
    }

    path("checkpoint") {
      entity(as[String]) { body =>
        withAuth {
          postJsonRoute {
            Try(Json.parse(body)).map { js =>
              js.validate[Checkpoint] match {
                case err: JsError =>
                  WrongTransactionJson(err).response
                case JsSuccess(checkpoint: Checkpoint, _) =>
                  validateCheckpoint(checkpoint) match {
                    case Some(apiError) => apiError.response
                    case None =>
                      coordinator ! BroadcastCheckpoint(checkpoint)
                      JsonResponse(Json.obj("message" -> "Checkpoint broadcasted"), StatusCodes.OK)
                  }
              }
            }.getOrElse(WrongJson().response)
          }
        }
      }
    }
  }
}
