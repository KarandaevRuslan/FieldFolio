package com.karandaev.fieldfolio.core.models

import com.karandaev.fieldfolio.core.document.*
import com.squareup.moshi.Moshi
import java.io.OutputStream
import java.util.zip.*

/** Portable unencrypted snapshot; no model binaries, endpoint credentials or account data. */
object ModelResultArchive {
  fun write(execution: ModelExecution, input: ByteArray?, moshi: Moshi, output: OutputStream) {
    val resources = linkedMapOf<String, ByteArray>()
    fun attach(source: String?): String? {
      source ?: return null
      val bytes = execution.files[source] ?: return null
      val path = "resources/result/asset-${resources.size}.bin"
      resources[path] = bytes
      return path
    }
    val original = execution.result
    val inputPath =
      if (input != null) "resources/result/input.img".also { resources[it] = input }
      else attach(original.inputAsset)
    val result =
      original.copy(
        inputAsset = inputPath,
        maskAsset = attach(original.maskAsset),
        layers = original.layers.map { it.copy(maskAsset = attach(it.maskAsset)) },
      )
    require(resources.values.sumOf { it.size.toLong() } <= 128L * 1024 * 1024)
    ZipOutputStream(output).use { zip ->
      fun entry(path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
      }
      entry("format.json", "{\"format\":\"GeoInspect.ModelResult\",\"version\":1}".toByteArray())
      entry("result.json", moshi.adapter(ModelResultData::class.java).toJson(result).toByteArray())
      resources.forEach { (path, bytes) -> entry(path, bytes) }
    }
  }
}
