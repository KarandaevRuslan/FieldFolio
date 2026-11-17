package com.karandaev.fieldfolio.core.ui.components.map.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.views.MapView

/** Connects an OSMDroid [MapView] to the current Compose lifecycle. */
@Composable
internal fun MapLifecycleEffect(mapView: MapView, onSaveCamera: () -> Unit = {}) {
  val save = rememberUpdatedState(onSaveCamera)
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(mapView, lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        else -> Unit
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      save.value()
      mapView.onPause()
      mapView.onDetach()
    }
  }
}
