package com.karandaev.fieldfolio.core.ui.components.map.controls.focusmenu.model

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerKind
import com.karandaev.fieldfolio.core.ui.components.map.marker.model.OsmMarkerSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsmTrackedFocusPointsTest {
  @Test
  fun collocatedParticipantsKeepTheirOwnIdentitiesButFocusShortcutIsNotDuplicated() {
    val point = MapPoint(50.0, 6.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = point,
        initialCenter = point,
        focusTargetTitle = "Target",
        initialCenterTitle = "Center",
        markers = listOf(OsmMarkerSpec("alex", point, OsmMarkerKind.Current, "Alex")),
        trackedPoints =
          listOf(
            OsmTrackedFocusPoint("device:self", "My current location", point, stale = false),
            OsmTrackedFocusPoint("participant:ivan", "Ivan", point, stale = true),
          ),
      )
    // Target at the same location is a duplicate navigation action, not a third person.
    assertEquals(4, menu.size)
    assertEquals(4, menu.map { it.id }.distinct().size)
    assertTrue(menu.none { it.id == "focus-target" })
    assertTrue(menu.any { it.title == "Ivan" && it.stale })
    assertTrue(menu.any { it.id == "initial-center" })
  }

  @Test
  fun addingLiveMarkersDoesNotDuplicateTheirRows() {
    val point = MapPoint(50.0, 6.0)
    val feeds =
      listOf(
        OsmTrackedFocusPoint("device:self", "Device", point, linkedMarkerId = "device"),
        OsmTrackedFocusPoint("participant:ivan", "Ivan", point),
      )
    val markers = mergeMarkersWithTrackedPoints(emptyList(), feeds)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = point,
        initialCenter = point,
        focusTargetTitle = "Focus",
        initialCenterTitle = "Center",
        markers = markers,
        trackedPoints = feeds,
      )
    assertEquals(3, menu.size)
    assertTrue(menu.none { it.id == "focus-target" })
    assertEquals(1, menu.count { it.title == "Device" })
    assertEquals(1, menu.count { it.title == "Ivan" })
  }

  @Test
  fun mainMapStaleDeviceIsOneDestinationEvenWhenFocusTargetHasOlderCoordinates() {
    val previouslyLocated = MapPoint(50.0, 6.0)
    val latestFix = MapPoint(50.1, 6.1)
    val tracked =
      OsmTrackedFocusPoint(
        id = "device:self",
        title = "My last known location",
        point = latestFix,
        linkedMarkerId = "current-location",
        stale = true,
      )
    val updated =
      mergeMarkersWithTrackedPoints(
        listOf(
          OsmMarkerSpec(
            "current-location",
            previouslyLocated,
            OsmMarkerKind.Current,
            "Last known location",
          )
        ),
        listOf(tracked),
      )
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = previouslyLocated,
        focusTargetMarkerId = "current-location",
        initialCenter = MapPoint(52.0, 7.0),
        focusTargetTitle = "Focus target",
        initialCenterTitle = "Map center",
        markers = updated,
        trackedPoints = listOf(tracked),
      )
    assertEquals(listOf("tracked-device:self", "initial-center"), menu.map { it.id })
    assertEquals(1, menu.count { it.title == "My last known location" })
  }

  @Test
  fun miniMapSelectedMarkerAndFocusTargetAreOneDestination() {
    val chosen = MapPoint(52.0, 7.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = chosen,
        focusTargetMarkerId = "selected-location",
        initialCenter = MapPoint(53.0, 8.0),
        focusTargetTitle = "Focus target",
        initialCenterTitle = "Map center",
        markers =
          listOf(
            OsmMarkerSpec("selected-location", chosen, OsmMarkerKind.Selected, "Selected location")
          ),
      )
    assertEquals(listOf("marker-selected-location", "initial-center"), menu.map { it.id })
  }

  @Test
  fun miniMapLocationAndFocusTargetAreOneDestination() {
    val device = MapPoint(52.0, 7.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = device,
        initialCenter = MapPoint(53.0, 8.0),
        focusTargetTitle = "Focus target",
        initialCenterTitle = "Map center",
        markers =
          listOf(
            OsmMarkerSpec("current-location", device, OsmMarkerKind.Current, "My current location")
          ),
      )
    assertEquals(listOf("current-current-location", "initial-center"), menu.map { it.id })
  }

  @Test
  fun distinctParticipantsWithSameCoordinatesAreNotRemoved() {
    val place = MapPoint(52.0, 7.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = place,
        initialCenter = MapPoint(53.0, 8.0),
        focusTargetTitle = "Focus target",
        initialCenterTitle = "Map center",
        markers =
          listOf(
            OsmMarkerSpec("alice", place, OsmMarkerKind.Current, "Alice"),
            OsmMarkerSpec("bob", place, OsmMarkerKind.Current, "Bob"),
          ),
      )
    assertEquals(1, menu.count { it.title == "Alice" })
    assertEquals(1, menu.count { it.title == "Bob" })
    assertTrue(menu.none { it.id == "focus-target" })
  }

  @Test
  fun noOptionalFeedsKeepsOnlyTargetAndCenter() {
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = MapPoint(1.0, 2.0),
        initialCenter = MapPoint(1.0, 2.0),
        focusTargetTitle = "Target",
        initialCenterTitle = "Center",
        markers = emptyList(),
      )
    assertEquals(listOf("focus-target", "initial-center"), menu.map { it.id })
  }

  @Test
  fun selectedMarkerAtOriginOutranksFartherDeviceLocation() {
    val selected = MapPoint(52.0, 7.0)
    val near = MapPoint(52.001, 7.0)
    val farDevice = MapPoint(53.0, 7.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = selected,
        focusTargetMarkerId = "chosen",
        initialCenter = MapPoint(52.1, 7.0),
        focusTargetTitle = "Focus target",
        initialCenterTitle = "Current map center",
        markers =
          listOf(
            OsmMarkerSpec("device", farDevice, OsmMarkerKind.Current, "My current location"),
            OsmMarkerSpec("chosen", selected, OsmMarkerKind.Selected, "Selected location"),
            OsmMarkerSpec("near", near, OsmMarkerKind.Note, "Nearby note"),
          ),
      )
    assertEquals(
      listOf("marker-chosen", "marker-near", "initial-center", "current-device"),
      menu.map { it.id },
    )
    assertTrue(menu.first().isOrigin)
  }

  @Test
  fun pointsCollocatedWithOriginAllComeFirstWithoutCollapsingIdentities() {
    val origin = MapPoint(52.0, 7.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = origin,
        initialCenter = MapPoint(53.0, 8.0),
        focusTargetTitle = "Focus target",
        initialCenterTitle = "Current map center",
        markers =
          listOf(
            OsmMarkerSpec("far", MapPoint(54.0, 8.0), OsmMarkerKind.Current, "Far participant"),
            OsmMarkerSpec("alice", origin, OsmMarkerKind.Note, "Alice"),
            OsmMarkerSpec("bob", origin, OsmMarkerKind.Note, "Bob"),
          ),
      )
    assertEquals(listOf("marker-alice", "marker-bob"), menu.take(2).map { it.id })
    assertTrue(menu.take(2).all { it.isOrigin })
    assertEquals(2, menu.count { it.isOrigin })
  }

  @Test
  fun optionalMapCenterCanBeHiddenWithoutChangingOtherDestinations() {
    val origin = MapPoint(52.0, 7.0)
    val values =
      listOf(
        OsmMarkerSpec("near", MapPoint(52.001, 7.0), OsmMarkerKind.Note, "Near"),
        OsmMarkerSpec("device", MapPoint(54.0, 7.0), OsmMarkerKind.Current, "Device"),
      )
    fun menu(show: Boolean) =
      buildOsmFocusMenuItems(
        focusTarget = origin,
        initialCenter = MapPoint(53.0, 7.0),
        focusTargetTitle = "Target",
        initialCenterTitle = "Current map center",
        markers = values,
        showCurrentMapCenter = show,
      )
    assertEquals(listOf("focus-target", "marker-near", "current-device"), menu(false).map { it.id })
    assertEquals(
      listOf("focus-target", "marker-near", "initial-center", "current-device"),
      menu(true).map { it.id },
    )
  }

  @Test
  fun whenNoFocusTargetMapCenterIsOriginUnlessItIsHidden() {
    val center = MapPoint(50.0, 6.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = null,
        initialCenter = center,
        focusTargetTitle = "Target",
        initialCenterTitle = "Center",
        markers =
          listOf(OsmMarkerSpec("remote", MapPoint(51.0, 6.0), OsmMarkerKind.Current, "Remote")),
      )
    assertEquals(listOf("initial-center", "current-remote"), menu.map { it.id })
    assertTrue(menu.first().isOrigin)
  }

  @Test
  fun explicitDistanceOriginTakesPrecedenceOverFocusTarget() {
    val origin = MapPoint(48.0, 6.0)
    val menu =
      buildOsmFocusMenuItems(
        focusTarget = MapPoint(50.0, 6.0),
        initialCenter = MapPoint(49.0, 6.0),
        focusTargetTitle = "Target",
        initialCenterTitle = "Center",
        markers = listOf(OsmMarkerSpec("origin", origin, OsmMarkerKind.Note, "Origin")),
        explicitDistanceOrigin = origin,
      )
    assertEquals("marker-origin", menu.first().id)
    assertTrue(menu.first().isOrigin)
  }
}
