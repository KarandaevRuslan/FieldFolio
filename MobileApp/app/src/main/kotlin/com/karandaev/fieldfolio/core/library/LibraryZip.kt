package com.karandaev.fieldfolio.core.library

import java.io.*
import java.util.zip.*

internal object LibraryZip {
  const val MAX_TOTAL = 128 * 1024 * 1024
  const val MAX_OBJECT = 64 * 1024 * 1024
  const val MAX_FILE = 48 * 1024 * 1024

  fun path(name: String) {
    require(
      name.length in 1..1024 &&
        !name.startsWith('/') &&
        name.none { it.isISOControl() || it in "\\:" } &&
        name.split('/').all { it.isNotBlank() && it !in setOf(".", "..") && it.length <= 120 }
    ) {
      "Unsafe archive path"
    }
  }

  fun read(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      if (count == 0) continue
      require(output.size().toLong() + count <= limit) { "File exceeds size limit" }
      output.write(buffer, 0, count)
    }
    return output.toByteArray()
  }

  fun pack(files: Map<String, ByteArray>, objectLimit: Int = MAX_FILE): ByteArray {
    require(files.size in 1..10000 && files.values.sumOf { it.size.toLong() } <= MAX_TOTAL)
    val result = ByteArrayOutputStream()
    ZipOutputStream(result).use { zip ->
      files.forEach { (name, bytes) ->
        path(name)
        require(bytes.size <= objectLimit)
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    return result.toByteArray().also { require(it.size <= MAX_TOTAL) }
  }

  fun unpack(bytes: ByteArray, objectLimit: Int = MAX_FILE): Map<String, ByteArray> {
    require(bytes.size <= MAX_TOTAL)
    val files = linkedMapOf<String, ByteArray>()
    var total = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        require(!entry.isDirectory && files.size < 10000)
        path(entry.name)
        require(entry.name !in files) { "Duplicate archive entry" }
        val data = read(zip, minOf(objectLimit, MAX_TOTAL - total))
        total += data.size
        files[entry.name] = data
        zip.closeEntry()
      }
    }
    require(files.isNotEmpty())
    files.keys.forEach { name ->
      var parent = name.substringBeforeLast('/', "")
      while (parent.isNotEmpty()) {
        require(parent !in files)
        parent = parent.substringBeforeLast('/', "")
      }
    }
    return files
  }
}
