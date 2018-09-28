package com.wavesplatform.lang.v1.evaluator.ctx.impl

import com.wavesplatform.lang.v1.compiler.CompilerContext
import com.wavesplatform.lang.v1.compiler.Types.{BOOLEAN, BYTEVECTOR, STRING}
import com.wavesplatform.lang.v1.evaluator.FunctionIds._
import com.wavesplatform.lang.v1.evaluator.ctx.{BaseFunction, EvaluationContext, NativeFunction}
import com.wavesplatform.lang.v1.{BaseGlobal, CTX}
import scodec.bits.ByteVector

object CryptoContext {

  def build(global: BaseGlobal): CTX = {
    def hashFunction(name: String, internalName: Short, cost: Long, docString: String)(h: Array[Byte] => Array[Byte]): BaseFunction =
      NativeFunction(name, cost, internalName, BYTEVECTOR, docString, ("bytes", BYTEVECTOR, "value")) {
        case (m: ByteVector) :: Nil => Right(ByteVector(h(m.toArray)))
        case _                      => ???
      }

    val keccak256F: BaseFunction  = hashFunction("keccak256", KECCAK256, 10, "256 bit Keccak/SHA-3/TIPS-202")(global.keccak256)
    val blake2b256F: BaseFunction = hashFunction("blake2b256", BLAKE256, 10, "256 bit BLAKE")(global.blake2b256)
    val sha256F: BaseFunction     = hashFunction("sha256", SHA256, 10, "256 bit SHA-2")(global.sha256)

    val sigVerifyF: BaseFunction =
      NativeFunction("sigVerify", 100, SIGVERIFY, BOOLEAN, "check signature", ("message", BYTEVECTOR, "value"), ("sig", BYTEVECTOR, "signature"), ("pub", BYTEVECTOR, "public key")) {
        case (m: ByteVector) :: (s: ByteVector) :: (p: ByteVector) :: Nil =>
          Right(global.curve25519verify(m.toArray, s.toArray, p.toArray))
        case _ => ???
      }

    def toBase58StringF: BaseFunction = NativeFunction("toBase58String", 10, TOBASE58, STRING, "Base58 encode", ("bytes", BYTEVECTOR, "value")) {
      case (bytes: ByteVector) :: Nil => global.base58Encode(bytes.toArray)
      case xs                         => notImplemented("toBase58String(bytes: byte[])", xs)
    }

    def fromBase58StringF: BaseFunction = NativeFunction("fromBase58String", 10, FROMBASE58, BYTEVECTOR,  "Base58 decode", ("str", STRING, "base58 encoded string")) {
      case (str: String) :: Nil => global.base58Decode(str, global.MaxBase58String).map(ByteVector(_))
      case xs                   => notImplemented("fromBase58String(str: String)", xs)
    }

    def toBase64StringF: BaseFunction = NativeFunction("toBase64String", 10, TOBASE64, STRING, "Base64 encode", ("bytes", BYTEVECTOR, "value")) {
      case (bytes: ByteVector) :: Nil => global.base64Encode(bytes.toArray)
      case xs                         => notImplemented("toBase64String(bytes: byte[])", xs)
    }

    def fromBase64StringF: BaseFunction = NativeFunction("fromBase64String", 10, FROMBASE64, BYTEVECTOR, "Base64 decode", ("str", STRING, "base64 encoded string")) {
      case (str: String) :: Nil => global.base64Decode(str).map(ByteVector(_))
      case xs                   => notImplemented("fromBase64String(str: String)", xs)
    }

    CTX(
      Seq.empty,
      Map.empty,
      Array(keccak256F, blake2b256F, sha256F, sigVerifyF, toBase58StringF, fromBase58StringF, toBase64StringF, fromBase64StringF)
    )
  }

  def evalContext(global: BaseGlobal): EvaluationContext   = build(global).evaluationContext
  def compilerContext(global: BaseGlobal): CompilerContext = build(global).compilerContext
}
