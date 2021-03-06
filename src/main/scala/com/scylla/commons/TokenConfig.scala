package com.scylla.commons

import java.util.UUID

import com.scylla.core.commons.CoreStaticActorRefs._
import com.scylla.core.models.InterUserToken
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim, JwtOptions}
import spray.json._
import com.scylla.commons.MessageCodes._
import com.scylla.core.actor.JWTSecretKeyGenerator.GetSecretKey
import akka.pattern.ask
import akka.util.Timeout
import com.scylla.core.commons.ConfigMother
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.Try

class TokenConfig extends ConfigMother {
  implicit val timout: Timeout = Timeout(1.seconds)
  private val algorithm: JwtAlgorithm.HS256.type = JwtAlgorithm.HS256
  private val jwtOptions: JwtOptions = JwtOptions(expiration = true)

  private def jwtSecretKey: String = Await.result((jwtSecretKeyActor ? GetSecretKey).mapTo[String], 1.seconds)
  private def decoding(token: String): Try[String] = Jwt.decode(token, jwtSecretKey, Seq(algorithm), jwtOptions)
  private def isTokenValid(token: String): Boolean = Jwt.isValid(token, jwtSecretKey, Seq(algorithm), jwtOptions)
  private def encoding(body: String): String = {
    var clim = JwtClaim(body)
    clim = clim.expiresIn(ONEDAYSECS_86400.toLong)
    Jwt.encode(
      clim,
      jwtSecretKey,
      algorithm
    )
  }

  def encodingUUID(uuid: UUID): String = {
    encoding(InterUserToken(uuid.toString).toJson.toString)
  }

  def decode2UUID(maybeToken: String): Either[Int, UUID] = {
    if (!maybeToken.trim.isEmpty) {
      decoding(maybeToken.trim).toEither match {
        case Right(jsonStr) =>
          try {
            Right(UUID.fromString(new JsonParser(jsonStr).parseJsValue(allowTrailingInput = true).convertTo[InterUserToken].uuid))
          } catch {
            case _: IllegalArgumentException =>
              Left(INVALID_JSON_FORMAT_2)
            case e: Throwable =>
              logger.warn(s"new exception when parsing uuid in [TokenConfig] message ${e.getStackTrace}")
              Left(PROBLEM_WITH_SERVER_500)
          }
        case Left(_) =>
          Left(TOKEN_IS_INVALID_3)
      }
    } else {
      Left(TOKEN_IS_EMPTY_1)
    }
  }

  def validateToken(maybeToken: String): Int = {
    if (!maybeToken.trim.isEmpty) {
      if (isTokenValid(maybeToken.trim)) {
        OK_200
      } else {
        TOKEN_IS_INVALID_3
      }
    } else {
      TOKEN_IS_EMPTY_1
    }
  }
}
