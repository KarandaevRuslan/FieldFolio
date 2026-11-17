package com.karandaev.fieldfolio.core.document

import android.app.Application
import android.media.MediaMetadataRetriever
import androidx.room.Room
import com.karandaev.fieldfolio.core.audio.AudioSeekState
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.library.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FinalAudioRegressionTest {
  @Test
  fun lateNativeSamplesAfterSeekCompletionCannotRestoreTheOldCursor() {
    var time = 0L
    val state = AudioSeekState { time }
    state.request(80000)
    state.complete()
    repeat(20) {
      state.observe(1000 + it)
      assertEquals(80000, state.position)
    }
    state.observe(80200)
    assertEquals(80200, state.position)
    state.request(5000)
    state.complete()
    time = 1600
    state.observe(7000)
    assertEquals(7000, state.position)
  }

  @Test
  fun lastSeekWinsAcrossSeveralNativeCompletions() {
    val state = AudioSeekState()
    assertEquals(1000, state.request(1000))
    assertNull(state.request(80000))
    assertEquals(80000, state.complete())
    assertNull(state.request(90000))
    assertEquals(90000, state.complete())
    state.complete()
    state.observe(80000)
    assertEquals(90000, state.position)
    state.observe(90000)
    assertEquals(90000, state.position)
  }

  @Test
  fun audioOverridesHideAndResetIndividualTagsWithoutMutatingOriginalMetadata() {
    val original = AudioMetadata(mapOf("title" to "Original", "artist" to "Artist"))
    val options =
      AudioPresentation(hiddenTags = listOf("artist"), overrides = mapOf("title" to "Override"))
    assertEquals(mapOf("title" to "Override"), original.displayed(options))
    assertEquals("Original", original.displayed(options.copy(overrides = emptyMap()))["title"])
    assertEquals("Artist", original.tags["artist"])
  }

  @Test
  fun metadataReaderUsesMediaDataSourceWithoutRequiringAnMp3Filename() = runBlocking {
    val source = org.robolectric.shadows.util.DataSource.toDataSource("MediaDataSource")
    ShadowMediaMetadataRetriever.addMetadata(
      source,
      MediaMetadataRetriever.METADATA_KEY_TITLE,
      "M4A track",
    )
    ShadowMediaMetadataRetriever.addMetadata(
      source,
      MediaMetadataRetriever.METADATA_KEY_ARTIST,
      "Author",
    )
    val bytes = "in-memory-container".toByteArray()
    val original = bytes.copyOf()
    try {
      val metadata = readAudioMetadata(bytes)
      assertEquals("M4A track", metadata.tags["title"])
      assertEquals("Author", metadata.tags["artist"])
      assertArrayEquals(original, bytes)
    } finally {
      ShadowMediaMetadataRetriever.reset()
    }
  }

  @Test
  fun olderDocumentsReceiveDefaultAudioOptionsAndNewDocumentsRetainOverrides() {
    val adapter = MoshiFactory.create().adapter(ReportDocument::class.java)
    val old = adapter.fromJson("""{"nodes":[{"id":"old","name":"Sound","kind":"AUDIO"}]}""")!!
    assertEquals(AudioPresentation(), old.nodes.single().audio)
    val options =
      AudioPresentation(
        showCover = false,
        overrides = mapOf("album" to "New"),
        coverAsset = "resources/cover.png",
      )
    val next = old.copy(nodes = listOf(old.nodes.single().copy(audio = options)))
    assertEquals(next, adapter.fromJson(adapter.toJson(next)))
    assertTrue("resources/cover.png" in next.resourcePaths())
  }

  @Test
  fun encryptedArchiveRoundTripRetainsAudioMetadataAndReplacementCover() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    db.libraryDao().state(LibraryState("initialized", "true"))
    val library = ReportLibrary(context, db, MoshiFactory.create())
    try {
      val options =
        AudioPresentation(
          hiddenTags = listOf("genre"),
          overrides = mapOf("title" to "Replacement"),
          coverAsset = "resources/cover.png",
        )
      val document =
        ReportDocument(
          nodes =
            listOf(
              DocumentNode(
                id = "audio",
                name = "sound.m4a",
                kind = BlockKind.AUDIO,
                asset = "resources/sound.m4a",
                audio = options,
              )
            )
        )
      val files =
        mapOf(
          "resources/sound.m4a" to byteArrayOf(1, 2, 3),
          "resources/cover.png" to byteArrayOf(4, 5, 6),
        )
      val id = library.saveDocument(0, null, "Audio report", "", document, files)
      val archive = library.exportBytes(listOf(id), "export-passwordAa1!".toCharArray())
      val imported =
        library.importBytes(archive, null, "export-passwordAa1!".toCharArray()).single()
      val loaded = library.loadDocument(imported)
      assertEquals(options, loaded.document.nodes.single().audio)
      assertArrayEquals(files[options.coverAsset], loaded.files[options.coverAsset])
    } finally {
      library.lockAll()
      db.close()
    }
  }
}
