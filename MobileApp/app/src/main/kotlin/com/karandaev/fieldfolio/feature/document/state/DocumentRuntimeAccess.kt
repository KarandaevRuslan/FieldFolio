package com.karandaev.fieldfolio.feature.document.state

import androidx.compose.runtime.*
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.text.*
import kotlinx.coroutines.*

internal fun DocumentViewModel.performDocumentAccess(
  weather: com.karandaev.fieldfolio.core.weather.WeatherProvider
): com.karandaev.fieldfolio.core.document.runtime.DocumentAccessContext {
  val name = displayName?.takeIf { it.isNotBlank() } ?: title
  val old = runtimeContext
  if (
    old != null &&
      old.document === document &&
      old.files === files &&
      old.title == name &&
      old.description == description
  )
    return old
  return com.karandaev.fieldfolio.core.document.runtime
    .DocumentAccessContext(
      runtimeScope,
      document,
      files,
      name,
      description,
      device = { runtimeLocation },
      weather = weather,
    )
    .also { runtimeContext = it }
}
