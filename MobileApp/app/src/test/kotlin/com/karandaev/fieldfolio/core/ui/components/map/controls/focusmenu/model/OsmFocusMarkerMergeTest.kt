package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerKind
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmFocusMarkerMergeTest {
  @Test
  fun deviceAppearsOnMapWhenLocationIsAvailable() {
    val point = MapPoint(50.12, 6.3)
    val device =
      OsmTrackedFocusPoint(
        id = "device:self",
        title = "My current location",
        point = point,
        linkedMarkerId = "current-location",
        showOnMap = true,
      )
    val markers = mergeMarkersWithTrackedPoints(emptyList(), listOf(device))
    assertEquals(1, markers.size)
    assertEquals("current-location", markers.single().id)
    assertEquals(point, markers.single().point)
    assertEquals(OsmMarkerKind.Current, markers.single().kind)
  }

  @Test
  fun collocatedParticipantsAreNotMergedByCoordinates() {
    val point = MapPoint(50.12, 6.3)
    val markers =
      mergeMarkersWithTrackedPoints(
        emptyList(),
        listOf(
          OsmTrackedFocusPoint("device:self", "Device", point),
          OsmTrackedFocusPoint("participant:a", "Alex", point),
        ),
      )
    assertEquals(2, markers.size)
    assertEquals(2, markers.map { it.id }.distinct().size)
  }

  @Test
  fun feedCanBeDisabledAndListOnlyPointsAreNotDrawn() {
    val point = MapPoint(50.12, 6.3)
    assertTrue(mergeMarkersWithTrackedPoints(emptyList(), emptyList()).isEmpty())
    val listOnly = OsmTrackedFocusPoint("remote", "Remote", point, showOnMap = false)
    assertTrue(mergeMarkersWithTrackedPoints(emptyList(), listOf(listOnly)).isEmpty())
  }

  @Test
  fun newerDeviceFixReplacesOldMainMapDeviceMarker() {
    val previous = MapPoint(50.0, 6.0)
    val latest = MapPoint(51.0, 7.0)
    val oldMarker = OsmMarkerSpec("current-location", previous, OsmMarkerKind.Current, "Old")
    val updated =
      mergeMarkersWithTrackedPoints(
        listOf(oldMarker),
        listOf(
          OsmTrackedFocusPoint(
            "device:self",
            "My current location",
            latest,
            linkedMarkerId = "current-location",
          )
        ),
      )
    assertEquals(1, updated.size)
    assertEquals(latest, updated.single().point)
    assertEquals("My current location", updated.single().title)
  }

  @Test
  fun focusFollowsUpdatedDeviceMarkerRatherThanOldSavedCoordinates() {
    val saved = MapPoint(50.0, 6.0)
    val live = MapPoint(51.0, 7.0)
    val markers =
      mergeMarkersWithTrackedPoints(
        listOf(OsmMarkerSpec("current-location", saved, OsmMarkerKind.Current, "Saved")),
        listOf(
          OsmTrackedFocusPoint("device:self", "Live", live, linkedMarkerId = "current-location")
        ),
      )
    assertEquals(live, resolveLinkedFocusTarget(saved, "current-location", markers))
    assertEquals(saved, resolveLinkedFocusTarget(saved, "unrelated", markers))
  }

  @Test
  fun unrelatedExistingMarkerIsNeverOverwritten() {
    val old =
      OsmMarkerSpec(
        "participant:a",
        MapPoint(50.0, 6.0),
        OsmMarkerKind.Note,
        "Alex",
        clickable = true,
      )
    val updated =
      mergeMarkersWithTrackedPoints(
        listOf(old),
        listOf(
          OsmTrackedFocusPoint(
            "a",
            "Another label",
            MapPoint(51.0, 7.0),
            linkedMarkerId = "participant:a",
          )
        ),
      )
    assertEquals(listOf(old), updated)
    assertTrue(updated.single().clickable)
  }
}
