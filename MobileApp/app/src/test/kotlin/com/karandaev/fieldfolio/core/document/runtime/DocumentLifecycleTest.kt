package com.karandaev.fieldfolio.core.document.runtime

import android.app.Application
import android.widget.FrameLayout
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.document.*
import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.library.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DocumentLifecycleTest {
  private val context
    get() = RuntimeEnvironment.getApplication()

  @Test
  fun miniFullscreenAndRecreatedContainerRetainTheSameRuntimeAndNexusRealm() {
    val pool = DocumentWebPool()
    val first = pool.acquire(context, "report", "doc")
    first.source = "live document source"
    first.lastSearchRequest = "consumed-search-request"
    val portrait = FrameLayout(context)
    pool.attach(first, portrait, true)
    pool.release(first)
    val landscape = pool.acquire(context, "report", "doc")
    assertSame(first, landscape)
    assertSame(first.view, landscape.view)
    assertEquals("consumed-search-request", landscape.lastSearchRequest)
    assertEquals("live document source", landscape.source)
    val full = FrameLayout(context)
    pool.attach(landscape, full, true)
    assertEquals(0, portrait.childCount)
    assertEquals(1, full.childCount)
    pool.clearReport("report")
    assertTrue(first.dead)
    assertEquals(0, full.childCount)
  }

  @Test
  fun distinctDocumentsReportsAndDraftsHaveDifferentCapabilitiesAndLockRevokesAllDrafts() {
    val pool = DocumentWebPool()
    val one = pool.acquire(context, "report", "one")
    val two = pool.acquire(context, "report", "two")
    val draft = pool.acquire(context, "report:draft:one", "one")
    val other = pool.acquire(context, "other-report", "one")
    assertEquals(
      4,
      setOf(one.client.host, two.client.host, draft.client.host, other.client.host).size,
    )
    assertEquals(
      4,
      setOf(one.client.scope, two.client.scope, draft.client.scope, other.client.scope).size,
    )
    pool.clearReport("report")
    assertTrue(one.dead)
    assertTrue(two.dead)
    assertTrue(draft.dead)
    assertFalse(other.dead)
    pool.clearReport("other-report")
  }

  @Test
  fun inactiveRuntimeCacheIsBoundedAndStaleLeaseCannotDeleteItsReplacement() {
    val pool = DocumentWebPool()
    val old = (0..5).map { pool.acquire(context, "report", "$it").also(pool::release) }
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
    assertEquals(2, old.count { it.dead })
    val replacement = pool.acquire(context, "report", "0")
    assertNotSame(old.first(), replacement)
    pool.release(old.first())
    assertSame(replacement, pool.acquire(context, "report", "0"))
    pool.clearReport("report")
  }

  @Test
  fun emptyDocumentPresentationAndExpandedWindowUseTheSharedReportRules() {
    val empty =
      DocumentNode(id = "doc", name = "Document", kind = BlockKind.DOCUMENT, hideTitle = true)
    assertFalse(hasLeafVisual(empty, emptyMap()))
    assertTrue(hasLeafVisual(empty.copy(body = DefaultNotebookDocument), emptyMap()))
    val hidden = empty.copy(body = DefaultNotebookDocument, visible = false)
    assertTrue(
      DocumentPresentation(DocumentTree(ReportDocument(nodes = listOf(hidden))), emptyMap())
        .children(null)
        .isEmpty()
    )
    val windows = NotebookWindows()
    windows.expanded = "doc"
    assertEquals("doc", windows.expanded)
    windows.clear()
    assertNull(windows.expanded)
  }

  @Test
  fun protectedArchiveAndCopyPreserveDocumentSourceAndSharedReportResources() = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    db.libraryDao().state(LibraryState("initialized", "true"))
    val library = ReportLibrary(context, db, MoshiFactory.create())
    try {
      val node =
        DocumentNode(
          id = "doc",
          name = "Notebook",
          kind = BlockKind.DOCUMENT,
          body = DefaultNotebookDocument,
          syntaxExtension = "html",
        )
      val image =
        DocumentNode(
          id = "image",
          name = "Image",
          kind = BlockKind.IMAGE,
          asset = "resources/image.png",
        )
      val files = mapOf("resources/image.png" to byteArrayOf(1, 2, 3, 4))
      val id =
        library.saveDocument(
          0,
          null,
          "Document report",
          "",
          ReportDocument(nodes = listOf(node, image)),
          files,
        )
      val archive = library.exportBytes(listOf(id), "Valid-Archive-9!".toCharArray())
      val imported = library.importBytes(archive, null, "Valid-Archive-9!".toCharArray()).single()
      val restored = library.loadDocument(imported)
      assertEquals(node, restored.document.nodes.first { it.id == "doc" })
      assertArrayEquals(files[image.asset], restored.files[image.asset])
      assertEquals(1, restored.files.size)
    } finally {
      library.lockAll()
      db.close()
    }
  }

  @Test
  fun newDocumentPreviewRetainsItsFolderRelativePathsAndDoesNotMutateTheStoredTree() = runBlocking {
    val folder = DocumentNode(id = "f", name = "Data", kind = BlockKind.FOLDER)
    val sibling =
      DocumentNode(
        id = "one",
        parentId = "f",
        name = "Dashboard",
        kind = BlockKind.TEXT,
        body = "saved",
      )
    val root =
      DocumentAccessContext(
        "report",
        ReportDocument(nodes = listOf(folder, sibling)),
        emptyMap(),
        "Title",
        "Description",
        device = { com.karandaev.fieldfolio.core.domain.model.location.MapPoint(1.0, 2.0) },
      )
    val draft =
      DocumentNode(
        id = "draft",
        parentId = "f",
        name = "Dashboard",
        kind = BlockKind.DOCUMENT,
        body = DefaultNotebookDocument,
      )
    val preview = documentDraftAccess(root, draft, emptyMap())
    val inserted = preview.document.nodes.single { it.id == "draft" }
    assertEquals("f", inserted.parentId)
    assertEquals("Dashboard (2)", inserted.name)
    assertEquals(1, inserted.order)
    assertEquals(2, root.document.nodes.size)
    assertEquals("saved", root.document.nodes.last().body)
    assertEquals(
      "/Data",
      preview
        .resources()
        .manifest(
          documentHost("report", "draft", "test"),
          "11111111-2222-3333-4444-555555555555",
          "draft",
        )
        .getString("base"),
    )
    assertEquals(root.device(), preview.device())
    assertEquals("Title", preview.title)
  }
}
