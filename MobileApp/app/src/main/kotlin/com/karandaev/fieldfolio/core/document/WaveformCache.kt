package com.karandaev.fieldfolio.core.document

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded completed envelopes, not audio. Disk entries are encrypted with an app Keystore key. */
object WaveformCache {
  private val mutex = Mutex()
  private val memory =
    object : LinkedHashMap<String, FloatArray>(128, .75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) =
        size > 128
    }
  private const val ALIAS = "geoinspect.waveform.v1"

  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let {
      return it
    }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      .apply {
        init(
          KeyGenParameterSpec.Builder(
              ALIAS,
              KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        )
      }
      .generateKey()
  }

  suspend fun get(
    bytes: ByteArray,
    context: Context? = null,
    decode: suspend () -> FloatArray = { decodeAudioWaveform(bytes) },
  ): FloatArray =
    withContext(Dispatchers.IO) {
      val digest =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
          "%02x".format(it.toInt() and 255)
        }
      // Serializing this small cache also deduplicates simultaneous requests for the same audio.
      mutex.withLock {
        memory[digest]?.let {
          return@withLock it.copyOf()
        }
        val directory =
          context?.let { File(it.noBackupFilesDir, "waveforms-v1").apply { mkdirs() } }
        val file = directory?.let { AtomicFile(File(it, digest)) }
        val cached =
          runCatching {
              val encrypted = requireNotNull(file).openRead().use { it.readBytes() }
              require(encrypted.size in 28..8192)
              val cipher = Cipher.getInstance("AES/GCM/NoPadding")
              cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, encrypted.copyOfRange(0, 12)),
              )
              cipher.updateAAD(digest.toByteArray())
              val buffer = ByteBuffer.wrap(cipher.doFinal(encrypted, 12, encrypted.size - 12))
              require(buffer.int == 1)
              val size = buffer.int
              require(size == 160 && buffer.remaining() == size * 4)
              FloatArray(size) { buffer.float }
                .also { wave -> require(wave.all { it.isFinite() && it in 0f..1f }) }
            }
            .getOrNull()
        val result = cached ?: decode()
        ensureActive()
        memory[digest] = result.copyOf()
        if (cached == null && file != null)
          runCatching {
            val plain = ByteBuffer.allocate(8 + result.size * 4).putInt(1).putInt(result.size)
            result.forEach(plain::putFloat)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(digest.toByteArray())
            val output = file.startWrite()
            try {
              output.write(cipher.iv + cipher.doFinal(plain.array()))
              file.finishWrite(output)
            } catch (e: Exception) {
              file.failWrite(output)
              throw e
            }
            directory!!
              .listFiles()
              ?.filter { it.isFile && !it.name.endsWith(".bak") }
              ?.sortedByDescending { it.lastModified() }
              ?.drop(256)
              ?.forEach { it.delete() }
          }
        result.copyOf()
      }
    }
}
