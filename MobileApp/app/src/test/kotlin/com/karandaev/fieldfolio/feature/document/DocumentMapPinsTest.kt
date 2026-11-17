package com.karandaev.fieldfolio.feature.document

import com.karandaev.fieldfolio.core.document.DocumentLocation
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.text.*
import com.karandaev.fieldfolio.feature.document.viewer.mapPinGroups
import com.karandaev.fieldfolio.feature.document.viewer.reportMapPins
import org.junit.Assert.*
import org.junit.Test

class DocumentMapPinsTest {
  @Test
  fun collidingPinsPreserveReportAndBlockIdentityWithoutAnInventedOrigin() {
    val one =
      InspectionReport(
        id = 1,
        title = "One",
        content = "",
        point = MapPoint(0.0, 0.0),
        lastUpdatedAtMillis = 1,
        documentLocations =
          listOf(
            DocumentLocation("a", "Site", 55.0, 37.0),
            DocumentLocation("b", "Weather", 55.0, 37.0),
            DocumentLocation("c", "Device", null, null, true),
          ),
      )
    val empty = one.copy(id = 2, documentLocations = emptyList())
    val two =
      one.copy(
        id = 3,
        documentLocations = listOf(DocumentLocation("d", "Another site", 55.0, 37.0)),
      )
    val pins = reportMapPins(listOf(one, empty, two), null)
    assertEquals(3, pins.size)
    assertEquals(
      setOf("1:a", "1:b", "3:d"),
      mapPinGroups(pins).values.single().map { "${it.reportId}:${it.location.nodeId}" }.toSet(),
    )
    assertEquals(4, reportMapPins(listOf(one, empty, two), MapPoint(54.0, 36.0)).size)
    assertEquals(1, reportMapPins(listOf(one.copy(documentLocations = null)), null).size)
  }
}
