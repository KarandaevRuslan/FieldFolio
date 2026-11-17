package com.karandaev.fieldfolio.core.library

import android.app.Application
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.document.ReportDocument
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class LibraryIdentityTest {
  private lateinit var db: AppDatabase
  private lateinit var library: ReportLibrary

  @Before
  fun setup() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    db.libraryDao().state(LibraryState("initialized", "true"))
    library = ReportLibrary(context, db, MoshiFactory.create())
  }

  @After
  fun close() {
    library.lockAll()
    db.close()
  }

  @Test
  fun foldersReportsAndRenamesUseTheSameUniqueNamespace() = runBlocking {
    val folder = library.createFolder("Name", null)
    val second = library.createFolder("name", null)
    assertEquals("name (2)", db.libraryDao().all().first { it.id == second }.name)
    val report = library.insert(InspectionReport(0, "NAME", "", 1, MapPoint(1.0, 2.0)))
    assertEquals("NAME (3)", db.libraryDao().all().first { it.id == report }.name)
    library.rename(folder, "name (2)")
    assertEquals("name (2) (2)", db.libraryDao().all().first { it.id == folder }.name)
  }

  @Test
  fun labelsSurviveProtectedArchiveRoundTripAndCopy() = runBlocking {
    val folder = library.createFolder("logical", null)
    library.rename(folder, "logical", "Отчёты")
    library.setPassword(folder, "password secretAa1!".toCharArray())
    val exported = library.exportBytes(listOf(folder), "archive secretAa1!".toCharArray())
    val copy = library.importBytes(exported, null, "archive secretAa1!".toCharArray()).single()
    val copied = db.libraryDao().all().first { it.id == copy }
    assertEquals("logical (2)", copied.name)
    assertEquals("Отчёты", copied.displayName)
    assertEquals("Отчёты", copied.displayLabel)
    library.unlock(copy, "password secretAa1!".toCharArray())
    library.rename(copy, copied.name, null)
    assertEquals(copied.name, db.libraryDao().all().first { it.id == copy }.displayLabel)
  }

  @Test
  fun blankReportTitlesAreRejectedBeforeCreationAndCannotEraseSavedData() = runBlocking {
    val report = InspectionReport(0, "Keep", "body", 1, MapPoint(1.0, 2.0))
    val id = library.insert(report)
    assertTrue(runCatching { library.insert(report.copy(title = " ")) }.isFailure)
    assertTrue(runCatching { library.update(report.copy(id = id, title = "")) }.isFailure)
    assertTrue(
      runCatching { library.saveDocument(id, null, "", "", ReportDocument(), emptyMap()) }.isFailure
    )
    assertEquals("Keep", library.getInspectionReportById(id)!!.title)
  }

  @Test
  fun editingDocumentTitleDoesNotRenameItsLogicalPath() = runBlocking {
    val id = library.saveDocument(0, null, "Original", "", ReportDocument(), emptyMap())
    library.saveDocument(id, null, "New display heading", "", ReportDocument(), emptyMap())
    assertEquals("Original", db.libraryDao().all().first { it.id == id }.name)
    assertEquals("New display heading", library.getInspectionReportById(id)!!.title)
  }

  @Test
  fun creationAndPropertiesPersistBothNamesAtomically() = runBlocking {
    val f = library.createFolder("Logical", null, "Displayed")
    assertEquals("Displayed", db.libraryDao().all().first { it.id == f }.displayName)
    val id =
      library.saveDocument(
        0,
        f,
        "Title",
        "body",
        ReportDocument(),
        emptyMap(),
        identity =
          com.karandaev.fieldfolio.core.names.ResourceIdentity("report.json", "Visible report"),
      )
    val loaded = library.loadDocument(id)
    assertEquals("report.json", loaded.name)
    assertEquals("Visible report", loaded.displayName)
    library.saveDocument(
      id,
      f,
      "Title",
      "body",
      loaded.document,
      loaded.files,
      identity = com.karandaev.fieldfolio.core.names.ResourceIdentity("renamed.json"),
    )
    val next = library.loadDocument(id)
    assertEquals("renamed.json", next.name)
    assertNull(next.displayName)
    assertTrue(
      runCatching {
          library.saveDocument(
            id,
            f,
            "Title",
            "",
            ReportDocument(),
            emptyMap(),
            identity = com.karandaev.fieldfolio.core.names.ResourceIdentity("", hideTitle = true),
          )
        }
        .isFailure
    )
  }

  @Test
  fun newWeakPasswordsAreRejectedAndExistingWeakArchivesRemainReadable() = runBlocking {
    val id = library.saveDocument(0, null, "Title", "", ReportDocument(), emptyMap())
    val weak = "12345678".toCharArray()
    assertTrue(runCatching { library.setPassword(id, weak) }.isFailure)
    assertTrue(weak.all { it == '\u0000' })
    assertNull(db.libraryDao().all().first { it.id == id }.lockId)
    assertTrue(runCatching { library.exportBytes(listOf(id), "12345678".toCharArray()) }.isFailure)
    val legacy =
      LibraryCrypto.encryptArchive(library.exportBytes(listOf(id)), "12345678".toCharArray())
    assertEquals(1, library.importBytes(legacy, null, "12345678".toCharArray()).size)
    library.setPassword(id, "Safer-resource9!".toCharArray())
    library.lockAll()
    library.unlock(id, "Safer-resource9!".toCharArray())
    assertEquals("Title", library.loadDocument(id).report.title)
  }

  @Test
  fun passwordPolicyReusesTheLoginScoreWithoutChangingIt() {
    for (value in
      listOf("12345678", "aaaaaaaaaaaaaaaa", "Password123!", "lowercase only long")) assertFalse(
      LibraryPasswordPolicy.accepts(value)
    )
    assertTrue(LibraryPasswordPolicy.accepts("Safer-resource9!"))
    assertEquals(
      com.karandaev.fieldfolio.core.security.password.PasswordStrengthCalculator.calculate(
        "Safer-resource9!"
      ),
      LibraryPasswordPolicy.score("Safer-resource9!"),
    )
  }

  @Test
  fun protectedDocumentArchivePreservesVisibilityAndEmptyTitles() = runBlocking {
    val node =
      com.karandaev.fieldfolio.core.document.DocumentNode(
        name = "Logical",
        kind = com.karandaev.fieldfolio.core.document.BlockKind.TEXT,
        body = "Retained",
        displayName = "Alias",
        hideTitle = true,
        visible = false,
      )
    val id =
      library.saveDocument(0, null, "Title", "", ReportDocument(nodes = listOf(node)), emptyMap())
    val zip = library.exportBytes(listOf(id), "Safer-resource9!".toCharArray())
    val copy = library.importBytes(zip, null, "Safer-resource9!".toCharArray()).single()
    val restored = library.loadDocument(copy).document.nodes.single()
    assertTrue(restored.hideTitle)
    assertFalse(restored.visible)
    assertEquals("Alias", restored.displayName)
    assertEquals("Retained", restored.body)
    assertEquals("Logical", restored.name)
  }
}
