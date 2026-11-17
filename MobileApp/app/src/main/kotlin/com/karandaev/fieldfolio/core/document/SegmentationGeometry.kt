package com.karandaev.fieldfolio.core.document

import com.squareup.moshi.JsonClass
import org.json.JSONArray
import org.json.JSONObject

@JsonClass(generateAdapter = true) data class NormalizedPoint(val x: Double, val y: Double)

@JsonClass(generateAdapter = true)
data class SegmentationPolygon(val rings: List<List<NormalizedPoint>>)

@JsonClass(generateAdapter = true)
data class SegmentationLayer(
  val label: String,
  val confidence: Double? = null,
  val maskAsset: String? = null,
  val polygons: List<SegmentationPolygon> = emptyList(),
  val originalPolygonJson: String? = null,
)

/** Normalized legacy points and GeoJSON Polygon/MultiPolygon; rings preserve holes. */
fun parseSegmentationPolygons(json: String?): List<SegmentationPolygon> {
  if (json.isNullOrBlank()) return emptyList()
  fun ring(a: JSONArray): List<NormalizedPoint> {
    require(a.length() in 3..100000)
    return (0 until a.length()).map { i ->
      val p = a.getJSONArray(i)
      val x = p.getDouble(0)
      val y = p.getDouble(1)
      require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
      NormalizedPoint(x, y)
    }
  }
  fun polygon(a: JSONArray) =
    SegmentationPolygon((0 until a.length()).map { ring(a.getJSONArray(it)) })
  val obj = JSONObject(json)
  val type = obj.optString("type").lowercase()
  return when {
    obj.has("points") -> listOf(SegmentationPolygon(listOf(ring(obj.getJSONArray("points")))))
    type == "polygon" -> listOf(polygon(obj.getJSONArray("coordinates")))
    type == "multipolygon" ->
      obj.getJSONArray("coordinates").let { a ->
        (0 until a.length()).map { polygon(a.getJSONArray(it)) }
      }
    else -> error("Unsupported polygon geometry")
  }
}
