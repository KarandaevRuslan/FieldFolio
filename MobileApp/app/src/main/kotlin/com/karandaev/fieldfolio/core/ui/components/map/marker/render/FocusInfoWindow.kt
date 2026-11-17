package com.karandaev.fieldfolio.core.ui.components.map.marker.render

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.MarkerPalette
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow

/** Rounded, themed focus tooltip; note markers retain their report-opening action. */
internal class FocusInfoWindow(map: MapView, palette: MarkerPalette) :
  InfoWindow(LinearLayout(map.context), map) {
  private val label = TextView(map.context)
  private val details = TextView(map.context)

  init {
    val density = map.resources.displayMetrics.density
    val body = mView as LinearLayout
    body.orientation = LinearLayout.VERTICAL
    body.setPadding(
      (16 * density).toInt(),
      (12 * density).toInt(),
      (16 * density).toInt(),
      (12 * density).toInt(),
    )
    body.elevation = 6 * density
    body.background =
      GradientDrawable().apply {
        setColor(palette.surfaceColor)
        cornerRadius = 16 * density
      }
    label.setTextColor(palette.textColor)
    label.textSize = 15f
    label.setTypeface(null, Typeface.BOLD)
    details.setTextColor(palette.textColor)
    details.textSize = 12f
    label.maxWidth = (240 * density).toInt()
    details.maxWidth = (240 * density).toInt()
    body.addView(label)
    body.addView(details)
    body.setOnClickListener { close() }
  }

  override fun onOpen(item: Any?) {
    val marker = item as? Marker ?: return
    label.text = marker.title.orEmpty()
    details.text =
      marker.subDescription?.takeIf { it.isNotBlank() }
        ?: String.format(
          java.util.Locale.getDefault(),
          "%.5f, %.5f",
          marker.position.latitude,
          marker.position.longitude,
        )
  }

  override fun onClose() {}
}
