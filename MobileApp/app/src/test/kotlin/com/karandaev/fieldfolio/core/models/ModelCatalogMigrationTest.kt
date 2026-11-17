package com.karandaev.fieldfolio.core.models

import android.app.Application
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.data.local.mlmodel.InspectionModelEntity
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModelSourceType
import com.karandaev.fieldfolio.core.json.MoshiFactory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ModelCatalogMigrationTest {
  @Test
  fun migrationCopiesExistingFilesKeepsMissingEntriesAndDoesNotResurrectDeletedModels() =
    runBlocking {
      val context = RuntimeEnvironment.getApplication()
      val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
      try {
        val source =
          File(context.filesDir, "previous.tflite").apply {
            writeBytes(byteArrayOf(0, 0, 0, 0) + "TFL3".toByteArray() + byteArrayOf(9, 8, 7))
          }
        val one =
          db
            .inspectionModelDao()
            .upsertModel(
              InspectionModelEntity(
                name = "Previous",
                resultType = InspectionResultType.DETECTION,
                sourceType = InspectionModelSourceType.FILE,
                sourcePath = source.absolutePath,
                inputWidth = 640,
                inputHeight = 640,
              )
            )
        val two =
          db
            .inspectionModelDao()
            .upsertModel(
              InspectionModelEntity(
                name = "Missing",
                resultType = InspectionResultType.SEGMENTATION,
                sourceType = InspectionModelSourceType.FILE,
                sourcePath = File(context.filesDir, "missing.tflite").absolutePath,
                inputWidth = 256,
                inputHeight = 256,
              )
            )
        var catalog = ModelCatalog(context, MoshiFactory.create(), db.inspectionModelDao())
        catalog.refresh()
        assertEquals(3, catalog.models.value.size)
        val migrated = catalog.models.value.single { it.id == "legacy-$one" }
        assertNull(migrated.legacySourcePath)
        assertArrayEquals(
          source.readBytes(),
          File(context.filesDir, "model_catalog/${migrated.fileName}").readBytes(),
        )
        assertNotNull(catalog.models.value.single { it.id == "legacy-$two" }.legacySourcePath)
        catalog.remove(migrated)
        catalog = ModelCatalog(context, MoshiFactory.create(), db.inspectionModelDao())
        catalog.refresh()
        assertFalse(catalog.models.value.any { it.id == migrated.id })
        assertTrue(source.exists())
        val zip = java.io.ByteArrayOutputStream()
        // Vocabulary is part of portable export; no native interpreter is needed for export.
        val missing = catalog.models.value.single { it.id == "legacy-$two" }
        val binary = File(context.filesDir, "model_catalog/${missing.fileName}")
        binary.writeBytes(source.readBytes())
        catalog.configure(
          missing.copy(
            settings = ModelSettings(vocabulary = listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]"))
          )
        )
        catalog.export(catalog.models.value.single { it.id == missing.id }, zip)
        val entries =
          java.util.zip.ZipInputStream(zip.toByteArray().inputStream()).use { z ->
            generateSequence { z.nextEntry?.name?.also { z.closeEntry() } }.toList()
          }
        assertEquals(setOf("model.json", "model.tflite", "vocab.txt"), entries.toSet())
      } finally {
        db.close()
      }
    }
}
