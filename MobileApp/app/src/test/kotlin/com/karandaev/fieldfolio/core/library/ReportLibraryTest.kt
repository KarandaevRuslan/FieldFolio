package com.karandaev.fieldfolio.core.library

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.data.local.report.seed.DefaultInspectionReportsSeed
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReportLibraryTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var library: ReportLibrary

  @Before
  fun setup() = runBlocking {
    context = RuntimeEnvironment.getApplication()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    db.libraryDao().state(LibraryState("initialized", "true"))
    library = ReportLibrary(context, db, MoshiFactory.create())
  }

  @After
  fun close() {
    library.lockAll()
    db.close()
  }

  private fun report(title: String = "Report") =
    InspectionReport(
      title = title,
      content = "private content",
      point = MapPoint(56.0, 92.0),
      lastUpdatedAtMillis = 123456789,
    )

  private suspend fun nodes() = db.libraryDao().all()

  private suspend fun create(parent: Long? = null, title: String = "Report") =
    library.saveWithImage(report(title), parent, null, false)

  private suspend fun roots() = nodes().filter { it.parentId == null }

  private suspend fun packet(id: Long) =
    library.access { library.readPacket(LibraryTree(nodes()), id) }

  private suspend fun expectFailure(block: suspend () -> Unit) {
    var failed = false
    try {
      block()
    } catch (_: Exception) {
      failed = true
    }
    assertTrue(failed)
  }

  @Test
  fun newReportsAreRootItemsUnlessParentWasExplicitlySpecified() = runBlocking {
    val folder = library.createFolder("Survey", null)
    library.currentFolder.value = folder
    val root = library.insert(report("Root"))
    val nested = create(folder)
    assertNull(nodes().single { it.id == root }.parentId)
    assertEquals(folder, nodes().single { it.id == nested }.parentId)
  }

  @Test
  fun subtreesCopyCutAndRenameWithoutLosingEmptyFoldersOrReusingIds() = runBlocking {
    val f = library.createFolder("F", null)
    val empty = library.createFolder("Empty", f)
    val a = create(f)
    val dest = library.createFolder("Target", null)
    library.transfer(listOf(f, a), dest, false)
    val copied = nodes().single { it.parentId == dest }
    assertEquals(2, nodes().count { it.parentId == copied.id })
    assertTrue(nodes().any { it.parentId == copied.id && it.name == "Empty" && it.folder })
    library.transfer(listOf(a, empty), dest, true)
    assertTrue(nodes().none { it.parentId == f })
    library.rename(a, "Renamed")
    assertEquals("private content", library.getInspectionReportById(a)!!.content)
    val highest = nodes().maxOf { it.id }
    library.delete(listOf(dest))
    val next = create()
    assertTrue(next > highest)
  }

  @Test
  fun invalidBatchMovesAndCyclesLeaveNoPartialTreeOrObjectRevisions() = runBlocking {
    val f = library.createFolder("F", null)
    val sub = library.createFolder("Sub", f)
    val a = create(f, "A")
    val b = create(f, "B")
    val dest = library.createFolder("Target", null)
    create(dest, "B")
    val before = nodes().map { it.id to it.payloadRef }
    val count = File(context.filesDir, "report-library/objects").listFiles()!!.size
    expectFailure { library.transfer(listOf(a, b), a, true) }
    expectFailure { library.transfer(listOf(f), sub, true) }
    assertEquals(before, nodes().map { it.id to it.payloadRef })
    assertEquals(count, File(context.filesDir, "report-library/objects").listFiles()!!.size)
  }

  @Test
  fun folderAndIndependentNestedPasswordsRemainRequiredAfterRestart() = runBlocking {
    val folder = library.createFolder("Private", null)
    val id = create(folder)
    library.setPassword(id, "report passwordAa1!".toCharArray())
    library.lockAll()
    // An outer password can wrap a still-locked inner report without decrypting it.
    library.setPassword(folder, "folder passwordAa1!".toCharArray())
    library.lockAll()
    library = ReportLibrary(context, db, MoshiFactory.create())
    expectFailure { library.getInspectionReportById(id) }
    expectFailure { library.unlock(folder, "wrong passwordAa1!".toCharArray()) }
    library.unlock(folder, "folder passwordAa1!".toCharArray())
    assertFalse(library.isUnlocked(id))
    library.unlock(id, "report passwordAa1!".toCharArray())
    assertEquals("private content", library.getInspectionReportById(id)!!.content)
    assertTrue(db.inspectionReportDao().getAllForLibraryMigration().isEmpty())
    val bytes = library.objects.read(nodes().single { it.id == id }.payloadRef!!)
    assertFalse(bytes.copyOfRange(0, 2).contentEquals(byteArrayOf(80, 75)))
  }

  @Test
  fun changingAndRemovingAnOuterPasswordDoesNotNeedOrRemoveInnerPassword() = runBlocking {
    val f = library.createFolder("Private", null)
    val id = create(f)
    library.setPassword(id, "inner passwordAa1!".toCharArray())
    library.lockAll()
    library.setPassword(f, "outer passwordAa1!".toCharArray())
    library.lockAll()
    library.unlock(f, "outer passwordAa1!".toCharArray())
    library.setPassword(f, "changed outerAa1!".toCharArray())
    library.setPassword(f, null)
    library.lockAll()
    expectFailure { library.getInspectionReportById(id) }
    library.unlock(id, "inner passwordAa1!".toCharArray())
    assertNotNull(library.getInspectionReportById(id))
  }

  @Test
  fun crossFolderMovesRewrapInheritedProtectionAndConfirmDeclassification() = runBlocking {
    val a = library.createFolder("A", null)
    val b = library.createFolder("B", null)
    val id = create(a)
    library.setPassword(id, "own passwordAa1!".toCharArray())
    library.setPassword(a, "first parentAa1!".toCharArray())
    library.setPassword(b, "second parentAa1!".toCharArray())
    library.lockAll()
    library.unlock(a, "first parentAa1!".toCharArray())
    library.unlock(b, "second parentAa1!".toCharArray())
    library.transfer(listOf(id), b, true)
    assertFalse(library.isUnlocked(id))
    expectFailure { library.transfer(listOf(id), null, true) }
    library.transfer(listOf(id), null, true, allowUnprotect = true)
    library.lockAll()
    library.unlock(id, "own passwordAa1!".toCharArray())
    assertEquals("private content", library.getInspectionReportById(id)!!.content)
  }

  @Test
  fun archiveRoundtripKeepsEmptyFoldersAndLockedSubtreesWithoutUnlocking() = runBlocking {
    val f = library.createFolder("Archive", null)
    library.createFolder("Empty", f)
    val id = create(f)
    library.setPassword(f, "folder passwordAa1!".toCharArray())
    library.lockAll()
    val zip = library.exportBytes(listOf(f))
    val copy = library.importBytes(zip, null).single()
    assertFalse(library.isUnlocked(copy))
    library.unlock(copy, "folder passwordAa1!".toCharArray())
    val child = nodes().single { it.parentId == copy && !it.folder }
    assertNotEquals(id, child.id)
    assertNotNull(library.getInspectionReportById(child.id))
    assertTrue(nodes().any { it.parentId == copy && it.name == "Empty" })
  }

  @Test
  fun exportingOneProtectedChildCarriesItsRequiredParentPasswordAndNoSiblings() = runBlocking {
    val f = library.createFolder("Private", null)
    val selected = create(f, "Selected")
    create(f, "Excluded")
    library.setPassword(f, "folder passwordAa1!".toCharArray())
    library.lockAll()
    val copy = library.importBytes(library.exportBytes(listOf(selected)), null).single()
    assertTrue(nodes().single { it.id == copy }.folder)
    library.unlock(copy, "folder passwordAa1!".toCharArray())
    assertEquals(listOf("Selected"), nodes().filter { it.parentId == copy }.map { it.name })
  }

  @Test
  fun encryptedArchiveAuthenticationAndMalformedImportAreAtomic() = runBlocking {
    val id = create()
    val password = "archive passwordAa1!".toCharArray()
    val bytes = library.exportBytes(listOf(id), password)
    assertTrue(password.all { it == '\u0000' })
    val before = nodes().size
    expectFailure { library.importBytes(bytes, null) }
    expectFailure { library.importBytes(bytes, null, "incorrect".toCharArray()) }
    assertEquals(before, nodes().size)
    assertEquals(1, library.importBytes(bytes, null, "archive passwordAa1!".toCharArray()).size)
    val manifest =
      """{"format":"geoinspect-library","version":1,"nodes":[{"id":1,"parentId":null,"name":"Partial","folder":true,"objectPath":null,"updatedAt":0,"lockId":null,"salt":null,"verifier":null,"iterations":600000},{"id":2,"parentId":1,"name":"Bad","folder":false,"objectPath":"objects/2.bin","updatedAt":0,"lockId":null,"salt":null,"verifier":null,"iterations":600000}]}"""
    val stable = nodes().size
    expectFailure {
      library.importBytes(
        LibraryZip.pack(
          mapOf("manifest.json" to manifest.toByteArray(), "objects/2.bin" to byteArrayOf(1, 2))
        ),
        null,
      )
    }
    assertEquals(stable, nodes().size)
    expectFailure {
      library.insert(report().copy(title = "T".repeat(1_000_000), content = "Ж".repeat(4_000_000)))
    }
    assertEquals(stable, nodes().size)
  }

  @Test
  fun protectedImportedSubtreeCanEnterAnotherProtectedFolderWhileItsOwnPasswordStaysLocked() =
    runBlocking {
      val a = library.createFolder("Original", null)
      create(a)
      library.setPassword(a, "own passwordAa1!".toCharArray())
      library.lockAll()
      val zip = library.exportBytes(listOf(a))
      val target = library.createFolder("Destination", null)
      library.setPassword(target, "parent passwordAa1!".toCharArray())
      val copy = library.importBytes(zip, target).single()
      assertFalse(library.isUnlocked(copy))
      library.lockAll()
      library.unlock(target, "parent passwordAa1!".toCharArray())
      library.unlock(copy, "own passwordAa1!".toCharArray())
      assertNotNull(library.getInspectionReportById(nodes().single { it.parentId == copy }.id))
    }

  @Test
  fun imageAndAllAnalysisResultsSurviveEditingEncryptionAndExport() = runBlocking {
    val full =
      DefaultInspectionReportsSeed.create().first {
        it.results.any { r -> r.segmentations.isNotEmpty() }
      }
    val mask = full.results.flatMap { it.segmentations }.firstOrNull()?.maskPath
    val normalized =
      full.copy(
        results =
          full.results.map {
            it.copy(segmentations = it.segmentations.map { s -> s.copy(maskPath = null) })
          }
      )
    val id =
      library.access {
        library.mutate { tx ->
          library.createReport(tx, ReportPacket(ReportSnapshot(report = normalized)), null)
        }
      }
    val image = File(context.cacheDir, "photo.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
    val original = library.getInspectionReportById(id)!!
    library.saveWithImage(
      original.copy(content = "edited", title = "New / title"),
      null,
      image.absolutePath,
      false,
    )
    assertEquals("New / title", library.getInspectionReportById(id)!!.title)
    val snapshot = packet(id)
    assertEquals(full.results.size, snapshot.snapshot.report.results.size)
    assertEquals(
      full.results.flatMap { it.segmentations }.size,
      snapshot.snapshot.report.results.flatMap { it.segmentations }.size,
    )
    library.setPassword(id, "secret passwordAa1!".toCharArray())
    library.lockAll()
    assertNull(LibraryImages.path(id))
    val copy = library.importBytes(library.exportBytes(listOf(id)), null).single()
    library.unlock(copy, "secret passwordAa1!".toCharArray())
    library.getInspectionReportById(copy)
    assertArrayEquals(byteArrayOf(1, 2, 3, 4), LibraryImages.data(LibraryImages.path(copy)))
    assertFalse(File(LibraryImages.path(copy)!!).exists())
    assertEquals("edited", library.getInspectionReportById(copy)!!.content)
  }

  @Test
  fun legacyJsonImportsWithoutOverwritingExistingIds() = runBlocking {
    val original = create()
    val json =
      com.karandaev.fieldfolio.core.json.exportjson.reports.buildInspectionReportExportJson(
        report("Legacy").copy(id = original),
        MoshiFactory.create(),
      )
    val id = library.importBytes(json.toByteArray(), null).single()
    assertNotEquals(original, id)
    assertNotNull(library.getInspectionReportById(original))
    assertEquals("Legacy", library.getInspectionReportById(id)!!.title)
  }

  @Test
  fun sqliteV7MigrationPreservesIdsFullResultsAndImagesAndDoesNotReseedEmptyLibraries() =
    runBlocking {
      library.lockAll()
      db.close()
      val name = "migration-${java.util.UUID.randomUUID()}.db"
      db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
      val full = DefaultInspectionReportsSeed.create().first()
      val id = db.inspectionReportDao().insert(full.copy(report = full.report.copy(id = 42)))
      val image =
        File(context.filesDir, "reports/$id.png").apply {
          parentFile!!.mkdirs()
          writeBytes(byteArrayOf(8, 9))
        }
      db.close()
      SQLiteDatabase.openDatabase(
          context.getDatabasePath(name).absolutePath,
          null,
          SQLiteDatabase.OPEN_READWRITE,
        )
        .use { sql ->
          sql.execSQL("DROP TABLE report_library_nodes")
          sql.execSQL("DROP TABLE report_library_state")
          sql.version = 7
        }
      db =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
          .addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
          .build()
      library = ReportLibrary(context, db, MoshiFactory.create())
      assertEquals(full.report.content, library.getInspectionReportById(42)!!.content)
      assertEquals(full.results.size, packet(42).snapshot.report.results.size)
      assertFalse(image.exists())
      assertArrayEquals(byteArrayOf(8, 9), packet(42).files.values.single())
      assertTrue(db.inspectionReportDao().getAllForLibraryMigration().isEmpty())
      library.delete(listOf(42))
      library = ReportLibrary(context, db, MoshiFactory.create())
      assertTrue(library.observeNodes().first().isEmpty())
    }

  @Test
  fun resetRetainsSeedSegmentationAndDoesNotRequireOldPasswords() = runBlocking {
    val id = create()
    library.setPassword(id, "secret passwordAa1!".toCharArray())
    library.lockAll()
    library.resetToDefaults()
    val all = nodes()
    assertEquals(DefaultInspectionReportsSeed.create().size, all.size)
    assertTrue(all.all { !it.folder && it.lockId == null })
    assertTrue(
      all.any { packet(it.id).snapshot.report.results.any { r -> r.segmentations.isNotEmpty() } }
    )
  }

  @Test
  fun exportingEqualFilenamesFromDifferentFoldersProducesUniquePortableRootNames() = runBlocking {
    val a = library.createFolder("A", null)
    val b = library.createFolder("B", null)
    val first = create(a, "Same")
    val second = create(b, "Same")
    val copies = library.importBytes(library.exportBytes(listOf(first, second)), null)
    val imported = nodes().filter { it.id in copies }
    assertEquals(2, imported.map { it.name }.distinct().size)
    assertTrue(imported.all { it.parentId == null })
    assertTrue(copies.all { library.getInspectionReportById(it)!!.title == "Same" })
    copies.forEach { id ->
      val snapshot = packet(id).snapshot.report
      assertEquals(id, snapshot.report.id)
      assertTrue(snapshot.results.all { it.result.inspectionReportId == id })
    }
  }

  @Test
  fun aValidVerifierCannotUnlockAnObjectWithATamperedCiphertext() = runBlocking {
    val id = create()
    library.setPassword(id, "secret passwordAa1!".toCharArray())
    library.lockAll()
    val node = nodes().single { it.id == id }
    val file = File(context.filesDir, "report-library/objects/" + node.payloadRef)
    val bytes = file.readBytes()
    bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
    file.writeBytes(bytes)
    expectFailure { library.unlock(id, "secret passwordAa1!".toCharArray()) }
    assertFalse(library.isUnlocked(id))
  }

  @Test
  fun aDamagedChildDoesNotBlockOtherReportsInAnUnlockedFolder() = runBlocking {
    val folder = library.createFolder("Private folder", null)
    val bad = create(folder, "Damaged")
    val good = create(folder, "Healthy")
    library.setPassword(folder, "folder passwordAa1!".toCharArray())
    library.lockAll()
    val file =
      File(context.filesDir, "report-library/objects/" + nodes().single { it.id == bad }.payloadRef)
    val bytes = file.readBytes()
    bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
    file.writeBytes(bytes)
    library.unlock(folder, "folder passwordAa1!".toCharArray())
    assertEquals("Healthy", library.getInspectionReportById(good)!!.title)
    expectFailure { library.getInspectionReportById(bad) }
  }

  @Test
  fun savingAPublicDraftAfterBackgroundingKeepsItsStoredImage() = runBlocking {
    val file =
      File(context.cacheDir, "session-photo.png").apply { writeBytes(byteArrayOf(7, 8, 9)) }
    val id = library.saveWithImage(report(), null, file.absolutePath, false)
    val original = library.getInspectionReportById(id)!!
    val oldReference = LibraryImages.path(id)!!
    library.lockAll()
    assertNull(LibraryImages.data(oldReference))
    library.saveWithImage(original.copy(content = "resumed edit"), null, oldReference, false)
    assertEquals("resumed edit", library.getInspectionReportById(id)!!.content)
    assertArrayEquals(byteArrayOf(7, 8, 9), LibraryImages.data(LibraryImages.path(id)))
  }

  @Test
  fun documentTreeMediaAndMultipleResultsSurviveProtectedCopyAndArchive() = runBlocking {
    val folder =
      com.karandaev.fieldfolio.core.document.DocumentNode(
        id = "group",
        name = "Group",
        kind = com.karandaev.fieldfolio.core.document.BlockKind.FOLDER,
      )
    val image =
      com.karandaev.fieldfolio.core.document.DocumentNode(
        id = "picture",
        parentId = folder.id,
        name = "Picture",
        kind = com.karandaev.fieldfolio.core.document.BlockKind.IMAGE,
        asset = "resources/doc/photo.png",
      )
    val run =
      com.karandaev.fieldfolio.core.document.ModelResultData(
        modelId = "one",
        modelName = "First model",
        provider = "LOCAL",
        task = "CLASSIFICATION",
        inputAsset = image.asset,
        scores = listOf(com.karandaev.fieldfolio.core.document.ResultScore("road", .9)),
      )
    val first =
      com.karandaev.fieldfolio.core.document.DocumentNode(
        id = "run1",
        parentId = folder.id,
        order = 1,
        name = "Run one",
        kind = com.karandaev.fieldfolio.core.document.BlockKind.MODEL_RESULT,
        result = run,
      )
    val second =
      first.copy(
        id = "run2",
        name = "Run two",
        order = 2,
        result = run.copy(id = "second-result", modelId = "two", provider = "SERVER"),
      )
    val document =
      com.karandaev.fieldfolio.core.document.ReportDocument(
        cover = image.asset,
        nodes = listOf(folder, image, first, second),
      )
    val id =
      library.saveDocument(
        0,
        null,
        "Document",
        "Summary",
        document,
        mapOf(image.asset!! to byteArrayOf(1, 2, 3)),
      )
    assertTrue(library.getInspectionReportById(id)!!.documentLocations!!.isEmpty())
    val loaded = library.loadDocument(id)
    assertEquals(document, loaded.document)
    library.setPassword(id, "document passwordAa1!".toCharArray())
    library.lockAll()
    expectFailure { library.loadDocument(id) }
    val copy =
      library
        .importBytes(
          library.exportBytes(listOf(id), "archive passwordAa1!".toCharArray()),
          null,
          "archive passwordAa1!".toCharArray(),
        )
        .single()
    expectFailure { library.loadDocument(copy) }
    library.unlock(copy, "document passwordAa1!".toCharArray())
    val imported = library.loadDocument(copy)
    assertEquals(document, imported.document)
    assertArrayEquals(byteArrayOf(1, 2, 3), imported.files[image.asset])
    assertEquals(2, imported.document.nodes.count { it.result != null })
    library.saveDocument(
      copy,
      null,
      "Edited",
      "Summary",
      document.copy(cover = null),
      imported.files,
      imported.report.lastUpdatedAtMillis,
    )
    expectFailure {
      library.saveDocument(
        copy,
        null,
        "Stale",
        "",
        document,
        imported.files,
        imported.report.lastUpdatedAtMillis,
      )
    }
    assertNull(library.loadDocument(copy).document.cover)
    assertEquals("Edited", library.getInspectionReportById(copy)!!.title)
  }

  @Test
  fun oldReportConvertsLazilyWithoutLosingMasksOrDetectionResults() = runBlocking {
    val id = create()
    val before = packet(id)
    assertNull(before.snapshot.document)
    val loaded = library.loadDocument(id)
    assertTrue(
      loaded.document.nodes.any {
        it.kind == com.karandaev.fieldfolio.core.document.BlockKind.LOCATION
      }
    )
    // Merely viewing an old packet must not rewrite it.
    assertNull(packet(id).snapshot.document)
    library.saveDocument(
      id,
      null,
      "Converted",
      "Content",
      loaded.document,
      loaded.files,
      loaded.report.lastUpdatedAtMillis,
    )
    assertEquals(2, packet(id).snapshot.version)
    assertEquals(loaded.document, library.loadDocument(id).document)
  }

  @Test
  fun allLegacySegmentationLayersBecomeVisualGeometryAndRemainInProtectedArchives() = runBlocking {
    val id = create()
    library.access {
      library.mutate { tx ->
        val tree = tx.tree()
        val node = tree.node(id)
        val old = library.readPacket(tree, id)
        val result =
          com.karandaev.fieldfolio.core.data.local.report.entity.InspectionResultEntity(
            id = 99,
            inspectionReportId = id,
            sortOrder = 0,
            title = "Segmentation",
            description = "",
            type =
              com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
                .SEGMENTATION,
          )
        val segments =
          listOf(
            com.karandaev.fieldfolio.core.data.local.report.entity.SegmentationEntity(
              inspectionResultId = 99,
              sortOrder = 0,
              clazz = "Crack",
              confidence = .9,
              maskPath = "resources/mask-a.png",
              polygonJson = """{"type":"polygon","points":[[0.1,0.1],[0.8,0.1],[0.8,0.8]]}""",
            ),
            com.karandaev.fieldfolio.core.data.local.report.entity.SegmentationEntity(
              inspectionResultId = 99,
              sortOrder = 1,
              clazz = "Repair",
              confidence = .8,
              maskPath = "resources/mask-b.png",
              polygonJson =
                """{"type":"Polygon","coordinates":[[[0.2,0.2],[0.6,0.2],[0.6,0.6]],[[0.3,0.3],[0.4,0.3],[0.4,0.4]]]}""",
            ),
          )
        val relation =
          old.snapshot.report.copy(
            results =
              listOf(
                com.karandaev.fieldfolio.core.data.local.report.relation
                  .InspectionResultWithPayload(
                    result = result,
                    detections = emptyList(),
                    segmentations = segments,
                  )
              )
          )
        val packet =
          old.copy(
            snapshot = old.snapshot.copy(report = relation),
            files =
              mapOf(
                "resources/mask-a.png" to byteArrayOf(1),
                "resources/mask-b.png" to byteArrayOf(2),
              ),
          )
        tx.payload(node, library.encrypt(tree, node, library.packets.encode(packet)))
      }
    }
    val bundle = library.loadDocument(id)
    val layers = bundle.document.nodes.single { it.result != null }.result!!.layers
    assertEquals(listOf("Crack", "Repair"), layers.map { it.label })
    assertEquals(2, layers[1].polygons.single().rings.size)
    library.saveDocument(
      id,
      null,
      "Segmentation",
      "",
      bundle.document,
      bundle.files,
      bundle.report.lastUpdatedAtMillis,
    )
    library.setPassword(id, "layers passwordAa1!".toCharArray())
    library.lockAll()
    val copy = library.importBytes(library.exportBytes(listOf(id)), null).single()
    library.unlock(copy, "layers passwordAa1!".toCharArray())
    assertEquals(
      layers,
      library.loadDocument(copy).document.nodes.single { it.result != null }.result!!.layers,
    )
    assertEquals(
      setOf("resources/mask-a.png", "resources/mask-b.png"),
      library.loadDocument(copy).files.keys,
    )
  }
}
