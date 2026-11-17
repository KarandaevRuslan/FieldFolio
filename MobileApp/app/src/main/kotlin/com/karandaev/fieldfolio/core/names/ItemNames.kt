package com.karandaev.fieldfolio.core.names

import java.text.Normalizer
import java.util.Locale

/**
 * Stable logical names identify siblings. A display label is optional and never resolves a path.
 */
object ItemNames {
  fun key(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

  fun valid(value: String): Boolean =
    value.trim().let {
      it.isNotBlank() && it.length <= 120 && it !in setOf(".", "..") && it.none(Char::isISOControl)
    }

  fun requireName(value: String): String =
    value.trim().also { require(valid(it)) { "Invalid item name" } }

  fun display(value: String?): String? =
    value
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.also { require(valid(it)) { "Invalid display name" } }

  fun unique(value: String, used: Collection<String>, notify: Boolean = false): String {
    val base = requireName(value)
    val keys = used.map(::key).toHashSet()
    if (key(base) !in keys) return base
    val dot = base.lastIndexOf('.').takeIf { it > 0 && it < base.lastIndex }
    val stem = if (dot == null) base else base.take(dot)
    val extension = if (dot == null) "" else base.substring(dot).take(40)
    var index = 2
    while (true) {
      val suffix = " ($index)"
      index++
      val candidate = stem.take(120 - extension.length - suffix.length) + suffix + extension
      if (key(candidate) !in keys) {
        if (notify) NameResolutionEvents.record(value, candidate)
        return candidate
      }
    }
  }
}
