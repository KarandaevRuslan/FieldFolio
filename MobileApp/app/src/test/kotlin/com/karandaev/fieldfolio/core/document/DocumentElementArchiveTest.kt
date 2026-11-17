package com.karandaev.fieldfolio.core.document

import android.app.Application
import com.karandaev.fieldfolio.core.document.runtime.*
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.weather.*
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocumentElementArchiveTest {
  private var networkCalls = 0
  private val weather =
    object : WeatherProvider {
      override suspend fun getWeather(latitude: Double, longitude: Double): Result<WeatherInfo> {
        networkCalls++
        error("No network in export")
      }

      override suspend fun cachedWeather(latitude: Double, longitude: Double) =
        WeatherInfo(12.5, 3, "UTC")
    }
  private val nodes =
    listOf(
      DocumentNode(id = "f", name = "Group", kind = BlockKind.FOLDER),
      DocumentNode(id = "sub", parentId = "f", name = "Sub", kind = BlockKind.FOLDER),
      DocumentNode(
        id = "text",
        parentId = "sub",
        name = "Notes",
        kind = BlockKind.TEXT,
        body = "Body",
        displayName = "Alias",
        hideTitle = true,
        visible = false,
      ),
      DocumentNode(
        id = "doc",
        parentId = "f",
        order = 1,
        name = "Notebook",
        kind = BlockKind.DOCUMENT,
        body = DefaultNotebookDocument,
      ),
      DocumentNode(
        id = "image",
        parentId = "f",
        order = 2,
        name = "Image",
        kind = BlockKind.IMAGE,
        asset = "image.png",
        mime = "image/png",
      ),
      DocumentNode(
        id = "audio",
        parentId = "f",
        order = 3,
        name = "Audio",
        kind = BlockKind.AUDIO,
        asset = "sound.m4a",
        audio = AudioPresentation(overrides = mapOf("title" to "Title"), coverAsset = "image.png"),
      ),
      DocumentNode(
        id = "location",
        parentId = "f",
        order = 4,
        name = "Location",
        kind = BlockKind.LOCATION,
        deviceLocation = true,
      ),
      DocumentNode(
        id = "weather",
        parentId = "f",
        order = 5,
        name = "Weather",
        kind = BlockKind.WEATHER,
        latitude = 1.0,
        longitude = 2.0,
      ),
      DocumentNode(
        id = "clock",
        parentId = "f",
        order = 6,
        name = "Clock",
        kind = BlockKind.CLOCK,
        deviceLocation = true,
      ),
      DocumentNode(
        id = "model",
        parentId = "f",
        order = 7,
        name = "Result",
        kind = BlockKind.MODEL_RESULT,
        result =
          ModelResultData(
            modelId = "m",
            modelName = "M",
            provider = "local",
            task = "cv_segmentation",
            inputAsset = "image.png",
            maskAsset = "mask.png",
            boxes = listOf(ResultBox("box", .9, .1, .2, .8, .9)),
            values = listOf(ResultValue("number", 3.0)),
            layers = listOf(SegmentationLayer("mask", .8, "mask.png")),
          ),
      ),
      DocumentNode(
        id = "outside",
        order = 1,
        name = "Not exported",
        kind = BlockKind.TEXT,
        body = "PRIVATE-OUTSIDE-SELECTION",
      ),
    )

  private fun context() =
    DocumentAccessContext(
      "report",
      ReportDocument(nodes = nodes),
      mapOf(
        "image.png" to byteArrayOf(1, 2, 3),
        "sound.m4a" to byteArrayOf(4, 5, 6),
        "mask.png" to byteArrayOf(7, 8),
      ),
      device = { MapPoint(10.0, 20.0) },
      weather = weather,
    )

  private fun unzip(bytes: ByteArray) =
    linkedMapOf<String, ByteArray>().also { result ->
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
          val e = zip.nextEntry ?: break
          result[e.name] = zip.readBytes()
        }
      }
    }

  @Test
  fun folderExportKeepsAllTypesNamesOrderFilesAndFullModelResultsWithoutEncryption() = runBlocking {
    val access = context()
    val result = unzip(DocumentElementArchive.bytes(access, listOf("f")))
    assertTrue(result.keys.containsAll(listOf("manifest.json", "document.json", "README.txt")))
    val metadata = JSONObject(result.getValue("manifest.json").decodeToString())
    assertFalse(metadata.getBoolean("encrypted"))
    assertEquals("Fieldfolio.VfsElements", metadata.getString("format"))
    val doc =
      MoshiFactory.create()
        .adapter(ReportDocument::class.java)
        .fromJson(result.getValue("document.json").decodeToString())!!
    assertEquals(nodes.dropLast(1).map { it.id }, doc.nodes.map { it.id })
    assertFalse(doc.nodes.any { it.id == "outside" })
    assertFalse(result.values.any { it.decodeToString().contains("PRIVATE-OUTSIDE-SELECTION") })
    val text = doc.nodes.single { it.id == "text" }
    assertEquals("Alias", text.displayName)
    assertTrue(text.hideTitle)
    assertFalse(text.visible)
    val model = doc.nodes.single { it.id == "model" }.result!!
    assertEquals(.9, model.boxes.single().confidence, .01)
    assertEquals(3.0, model.values.single().value, .01)
    assertEquals(model.maskAsset, model.layers.single().maskAsset)
    assertArrayEquals(access.files["mask.png"], result[model.maskAsset])
    assertEquals(3, result.keys.count { it.startsWith("resources/") })
    assertEquals(access.document.nodes, nodes)
  }

  @Test
  fun widgetsHaveExplicitOfflineSnapshotsAndAudioKeepsTagsAndCover() = runBlocking {
    val result = unzip(DocumentElementArchive.bytes(context(), listOf("f")))
    val manifest = JSONObject(result.getValue("manifest.json").decodeToString())
    val snapshots = manifest.getJSONObject("widgets")
    assertEquals(10.0, snapshots.getJSONObject("location").getDouble("latitude"), .001)
    assertEquals(
      12.5,
      snapshots.getJSONObject("weather").getJSONObject("weather").getDouble("temperature"),
      .001,
    )
    assertTrue(snapshots.getJSONObject("clock").getLong("capturedAt") > 0)
    assertTrue(snapshots.getJSONObject("clock").isNull("latitude"))
    assertEquals(0, networkCalls)
    val audio = manifest.getJSONObject("audio").getJSONObject("audio")
    assertEquals("Title", audio.getJSONObject("displayedTags").getString("title"))
    assertNotNull(result[audio.getString("cover")])
  }

  @Test
  fun selectingALeafKeepsItsAncestorPathButNotSiblingContent() = runBlocking {
    val result = unzip(DocumentElementArchive.bytes(context(), listOf("text")))
    val doc =
      MoshiFactory.create()
        .adapter(ReportDocument::class.java)
        .fromJson(result.getValue("document.json").decodeToString())!!
    assertEquals(setOf("f", "sub", "text"), doc.nodes.map { it.id }.toSet())
    assertEquals("/Group/Sub/Notes", DocumentTree(doc).path("text"))
    assertEquals(0, result.keys.count { it.startsWith("resources/") })
  }

  @Test
  fun missingAssetsAndInvalidSelectionsFailRatherThanExportingBrokenMetadata() = runBlocking {
    assertTrue(runCatching { DocumentElementArchive.bytes(context(), emptyList()) }.isFailure)
    assertTrue(runCatching { DocumentElementArchive.bytes(context(), listOf("missing")) }.isFailure)
    val noFiles = DocumentAccessContext("report", context().document, emptyMap())
    assertTrue(runCatching { DocumentElementArchive.bytes(noFiles, listOf("image")) }.isFailure)
  }

  @Test
  @Config(shadows = [EmbeddedArtwork::class])
  fun sharedEmbeddedArtworkIsDecodedAndExportedOnce() = runBlocking {
    EmbeddedArtwork.reads = 0
    val doc =
      ReportDocument(
        nodes =
          listOf(
            DocumentNode(id = "a", name = "A", kind = BlockKind.AUDIO, asset = "track.m4a"),
            DocumentNode(
              id = "b",
              order = 1,
              name = "B",
              kind = BlockKind.AUDIO,
              asset = "track.m4a",
            ),
          )
      )
    val output =
      unzip(
        DocumentElementArchive.bytes(
          DocumentAccessContext("audio", doc, mapOf("track.m4a" to byteArrayOf(1, 2, 3))),
          listOf("a", "b"),
        )
      )
    val audio = JSONObject(output.getValue("manifest.json").decodeToString()).getJSONObject("audio")
    assertEquals(
      audio.getJSONObject("a").getString("cover"),
      audio.getJSONObject("b").getString("cover"),
    )
    assertTrue(audio.getJSONObject("a").getString("cover").endsWith(".png"))
    assertEquals(2, output.keys.count { it.startsWith("resources/") })
    assertEquals(1, EmbeddedArtwork.reads)
  }

  @org.robolectric.annotation.Implements(android.media.MediaMetadataRetriever::class)
  class EmbeddedArtwork : org.robolectric.shadows.ShadowMediaMetadataRetriever() {
    companion object {
      var reads = 0
    }

    @org.robolectric.annotation.Implementation
    override fun setDataSource(source: android.media.MediaDataSource) {}

    @org.robolectric.annotation.Implementation
    override fun extractMetadata(key: Int): String? = null

    @org.robolectric.annotation.Implementation
    fun getEmbeddedPicture(): ByteArray {
      reads++
      return byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3)
    }
  }
}
