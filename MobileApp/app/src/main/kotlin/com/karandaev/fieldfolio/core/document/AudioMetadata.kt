package com.karandaev.fieldfolio.core.document

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.*

data class AudioMetadata(val tags: Map<String, String> = emptyMap(), val cover: ByteArray? = null) {
  companion object {
    val TAGS = listOf("title", "artist", "album", "genre", "year", "track", "composer")
  }

  fun displayed(options: AudioPresentation): Map<String, String> =
    (tags + options.overrides)
      .filterKeys { it !in options.hiddenTags }
      .filterValues { it.isNotBlank() }
}

suspend fun readAudioMetadata(bytes: ByteArray): AudioMetadata =
  withContext(Dispatchers.IO) {
    val source = ByteArrayMediaSource(bytes, ownsCopy = false)
    val reader = MediaMetadataRetriever()
    try {
      reader.setDataSource(source)
      val keys =
        listOf(
          MediaMetadataRetriever.METADATA_KEY_TITLE,
          MediaMetadataRetriever.METADATA_KEY_ARTIST,
          MediaMetadataRetriever.METADATA_KEY_ALBUM,
          MediaMetadataRetriever.METADATA_KEY_GENRE,
          MediaMetadataRetriever.METADATA_KEY_YEAR,
          MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
          MediaMetadataRetriever.METADATA_KEY_COMPOSER,
        )
      val tags =
        AudioMetadata.TAGS.zip(keys)
          .mapNotNull { (tag, key) ->
            reader.extractMetadata(key)?.takeIf { it.isNotBlank() }?.let { tag to it.take(500) }
          }
          .toMap()
      AudioMetadata(
        tags,
        runCatching { reader.embeddedPicture?.takeIf { it.size <= 8 * 1024 * 1024 } }.getOrNull(),
      )
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      AudioMetadata()
    } finally {
      runCatching { reader.release() }
      source.close()
    }
  }
