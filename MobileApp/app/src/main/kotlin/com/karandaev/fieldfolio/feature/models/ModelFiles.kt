package com.karandaev.fieldfolio.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.editor.*
import com.karandaev.fieldfolio.feature.document.route.*
import com.karandaev.fieldfolio.feature.document.state.*
import com.karandaev.fieldfolio.feature.document.viewer.*
import kotlinx.coroutines.*

suspend fun readPickedBytes(
  context: android.content.Context,
  uri: android.net.Uri,
  max: Int,
): ByteArray =
  withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
      val output = java.io.ByteArrayOutputStream()
      val b = ByteArray(8192)
      while (true) {
        val n = input.read(b)
        if (n < 0) break
        if (n == 0) continue
        require(output.size().toLong() + n <= max) { "File exceeds size limit" }
        output.write(b, 0, n)
      }
      output.toByteArray()
    } ?: error("Cannot open selected file")
  }

suspend fun pickedName(context: android.content.Context, uri: android.net.Uri): String =
  withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver
          .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
          ?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
          }
      }
      .getOrNull()
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.let { com.karandaev.fieldfolio.core.names.ItemNames.requireName(it.take(120)) }
      ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf {
        com.karandaev.fieldfolio.core.names.ItemNames.valid(it)
      }
      ?: "image"
  }
