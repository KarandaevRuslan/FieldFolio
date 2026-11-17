package com.karandaev.fieldfolio.core.library

import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object LibraryCrypto {
  const val ITERATIONS = 600_000
  private val random = SecureRandom()
  private val magic = "GEOINSP1".toByteArray(Charsets.US_ASCII)

  fun salt() = ByteArray(16).also(random::nextBytes)

  fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
    try {
      require(
        password.isNotEmpty() &&
          password.size <= 1024 &&
          salt.size == 16 &&
          iterations in 100_000..1_200_000
      )
      val spec = PBEKeySpec(password, salt, iterations, 256)
      return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
      } finally {
        spec.clearPassword()
      }
    } finally {
      password.fill('\u0000')
    }
  }

  fun seal(bytes: ByteArray, key: ByteArray, aad: String): ByteArray {
    val iv = ByteArray(12).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(aad.toByteArray())
    return iv + cipher.doFinal(bytes)
  }

  fun open(bytes: ByteArray, key: ByteArray, aad: String): ByteArray {
    require(bytes.size >= 28)
    return try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key, "AES"),
        GCMParameterSpec(128, bytes.copyOfRange(0, 12)),
      )
      cipher.updateAAD(aad.toByteArray())
      cipher.doFinal(bytes, 12, bytes.size - 12)
    } catch (e: Exception) {
      throw LibraryPasswordException()
    }
  }

  fun protected(bytes: ByteArray) =
    bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)

  fun encryptArchive(bytes: ByteArray, password: CharArray): ByteArray {
    val salt = salt()
    val key = derive(password, salt)
    val header = magic + ByteBuffer.allocate(4).putInt(ITERATIONS).array() + salt
    return try {
      header + seal(bytes, key, header.toHex())
    } finally {
      key.fill(0)
    }
  }

  fun archiveOutput(output: OutputStream, password: CharArray): OutputStream {
    val salt = salt()
    val key = derive(password, salt)
    val header = magic + ByteBuffer.allocate(4).putInt(ITERATIONS).array() + salt
    val iv = ByteArray(12).also(random::nextBytes)
    try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
      cipher.updateAAD(header.toHex().toByteArray())
      output.write(header)
      output.write(iv)
      return CipherOutputStream(output, cipher)
    } finally {
      key.fill(0)
      password.fill('\u0000')
    }
  }

  fun decryptArchive(bytes: ByteArray, password: CharArray): ByteArray {
    try {
      require(protected(bytes) && bytes.size >= 56)
      val iterations = ByteBuffer.wrap(bytes, 8, 4).int
      val key = derive(password, bytes.copyOfRange(12, 28), iterations)
      return try {
        open(bytes.copyOfRange(28, bytes.size), key, bytes.copyOfRange(0, 28).toHex())
      } finally {
        key.fill(0)
      }
    } finally {
      password.fill('\u0000')
    }
  }

  private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
