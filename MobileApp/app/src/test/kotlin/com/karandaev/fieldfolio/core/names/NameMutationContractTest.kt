package com.karandaev.fieldfolio.core.names

import android.app.Application
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.library.*
import com.karandaev.fieldfolio.core.models.*
import com.karandaev.fieldfolio.core.ui.components.actions.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class NameMutationContractTest {
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
  fun moveCollisionsResolveInsideOneTransactionAndKeepPayloads() = runBlocking {
    val one = library.createFolder("One", null)
    val two = library.createFolder("Two", null)
    val a = library.saveDocument(0, one, "Same", "alpha", ReportDocument(), emptyMap())
    val b = library.saveDocument(0, two, "same", "beta", ReportDocument(), emptyMap())
    library.transfer(listOf(a), two, true)
    val all = db.libraryDao().all()
    assertEquals("Same (2)", all.single { it.id == a }.name)
    assertEquals("alpha", library.loadDocument(a).report.content)
    assertEquals("beta", library.loadDocument(b).report.content)
    assertEquals(two, all.single { it.id == a }.parentId)
    assertTrue(all.groupBy { it.parentId to ItemNames.key(it.name) }.all { it.value.size == 1 })
    val event =
      withTimeout(1000) {
        NameResolutionEvents.events.first { it.requested == "Same" && it.resolved == "Same (2)" }
      }
    assertEquals("Same (2)", event.resolved)
  }

  @Test
  fun failedTransactionsDoNotAnnounceNamesThatWereNotCommitted() = runBlocking {
    runCatching {
      NameResolutionEvents.transaction {
        withContext(Dispatchers.Default) {
          ItemNames.unique("Never committed", listOf("Never committed"), notify = true)
        }
        error("rollback")
      }
    }
    assertNull(
      withTimeoutOrNull(50) {
        NameResolutionEvents.events.first { it.requested == "Never committed" }
      }
    )
  }

  @Test
  fun modelsAllocateNamesOnRefreshAndConfigurationIncludingBuiltInCollisions() = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val moshi = MoshiFactory.create()
    val catalog = ModelCatalog(context, moshi)
    catalog.refresh()
    val seed = catalog.models.value.single()
    val one = LocalModel("one", "é", ModelAdapter.NUMERIC_REGRESSION, fileName = "one.tflite")
    val two = LocalModel("two", "e\u0301", ModelAdapter.NUMERIC_REGRESSION, fileName = "two.tflite")
    File(context.filesDir, "model_catalog/catalog.json")
      .writeText(
        moshi
          .adapter(ModelCatalogState::class.java)
          .toJson(ModelCatalogState(models = listOf(one, two)))
      )
    catalog.refresh()
    assertEquals("e\u0301 (2)", catalog.models.value.single { it.id == "two" }.name)
    catalog.configure(catalog.models.value.single { it.id == "two" }.copy(name = seed.name))
    assertEquals(seed.name + " (2)", catalog.models.value.single { it.id == "two" }.name)
    val restored = ModelCatalog(context, moshi)
    restored.refresh()
    assertEquals(catalog.models.value.map { it.name }, restored.models.value.map { it.name })
    assertTrue(
      runCatching {
          catalog.configure(restored.models.value.single { it.id == "one" }.copy(name = " "))
        }
        .isFailure
    )
  }

  @Test
  fun documentNamespaceIsSharedByFilesAndFoldersAcrossEveryMutation() {
    val f = DocumentNode(id = "f", name = "Folder", kind = BlockKind.FOLDER)
    val a = DocumentNode(id = "a", parentId = "f", name = "A.txt", kind = BlockKind.TEXT_FILE)
    val b = DocumentNode(id = "b", name = "a.TXT", kind = BlockKind.FOLDER)
    var doc = DocumentTree(ReportDocument(nodes = listOf(f, a, b))).transfer(listOf("a"), null)
    assertEquals("A (2).txt", DocumentTree(doc).node("a").name)
    doc = DocumentTree(doc).update(DocumentTree(doc).node("b").copy(name = "A (2).TXT"))
    assertEquals("A (2) (2).TXT", DocumentTree(doc).node("b").name)
    doc = DocumentTree(doc).transfer(listOf("a", "b"), null, copy = true)
    assertTrue(
      doc.nodes.groupBy { it.parentId to ItemNames.key(it.name) }.all { it.value.size == 1 }
    )
  }

  @Test
  fun selectionControlsAreContextualAndKeepSelectAllDuringSelection() {
    assertEquals(listOf(VfsSelectionAction.SELECT_ALL), vfsSelectionActions(true, 0, false))
    val one = vfsSelectionActions(true, 1, true)
    assertTrue(
      one.indexOf(VfsSelectionAction.SELECT_ALL) < one.indexOf(VfsSelectionAction.CLEAR_SELECTION)
    )
    assertTrue(one.indexOf(VfsSelectionAction.RENAME) < one.indexOf(VfsSelectionAction.COPY))
    assertTrue(
      one.containsAll(listOf(VfsSelectionAction.CLEAR_CLIPBOARD, VfsSelectionAction.PASTE))
    )
    assertFalse(vfsSelectionActions(true, 2, false).contains(VfsSelectionAction.RENAME))
    assertTrue(vfsSelectionActions(false, 0, false).isEmpty())
  }

  @Test
  fun headingCanUseItsTitleWithoutAnySupplementalText() {
    val heading = DocumentNode(name = "Visible heading", kind = BlockKind.HEADING)
    val p = DocumentPresentation(DocumentTree(ReportDocument(nodes = listOf(heading))), emptyMap())
    assertTrue(p.hasVisual(heading))
    val hidden = heading.copy(hideTitle = true)
    assertFalse(
      DocumentPresentation(DocumentTree(ReportDocument(nodes = listOf(hidden))), emptyMap())
        .hasVisual(hidden)
    )
  }
}
