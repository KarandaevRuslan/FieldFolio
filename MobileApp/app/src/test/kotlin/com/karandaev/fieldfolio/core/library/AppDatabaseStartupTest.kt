package com.karandaev.fieldfolio.core.library

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.karandaev.fieldfolio.core.data.local.AppDatabase
import com.karandaev.fieldfolio.core.data.local.report.mapper.toEntityWithResults
import com.karandaev.fieldfolio.core.domain.model.InspectionReport
import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.json.MoshiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.util.ReflectionHelpers

/** Exercises the real production builder and callbacks, including native Android SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AppDatabaseStartupTest {
  private lateinit var context: Context
  private var database: AppDatabase? = null

  @Before
  fun setup() {
    context = RuntimeEnvironment.getApplication()
    ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    context.deleteDatabase("geoinspect_database")
  }

  @After
  fun close() {
    database?.close()
    ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
  }

  private suspend fun opened(): AppDatabase =
    withContext(Dispatchers.IO) {
      val db = AppDatabase.getDatabase(context)
      database = db
      val sql = db.openHelper.writableDatabase
      sql.query("PRAGMA secure_delete").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
      }
      assertEquals(9, sql.version)
      db
    }

  private suspend fun seedCompleted(db: AppDatabase) {
    withTimeout(15000) { db.inspectionModelDao().observeModels().first { it.isNotEmpty() } }
    assertNotNull(db.settingsDao().getSettings())
  }

  private fun report(id: Long = 0) =
    InspectionReport(id, "Existing report", "Keep this content", 12345, MapPoint(56.0, 92.0))

  @Test
  fun freshDatabaseOpensWithItsProductionStartupCallbacks() = runBlocking {
    val db = opened()
    seedCompleted(db)
    val library = ReportLibrary(context, db, MoshiFactory.create())
    assertTrue(library.rootIds().isNotEmpty())
    library.lockAll()
  }

  @Test
  fun existingV8DatabaseReopensAndKeepsItsReport() = runBlocking {
    val db = opened()
    seedCompleted(db)
    val library = ReportLibrary(context, db, MoshiFactory.create())
    val id = library.insert(report())
    library.lockAll()
    db.close()
    ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    val reopened = opened()
    val restored = ReportLibrary(context, reopened, MoshiFactory.create())
    assertEquals("Keep this content", restored.getInspectionReportById(id)!!.content)
    restored.lockAll()
  }

  @Test
  fun existingV7DatabaseMigratesThroughTheProductionBuilderWithoutLosingReports() = runBlocking {
    val old = Room.databaseBuilder(context, AppDatabase::class.java, "geoinspect_database").build()
    try {
      old.inspectionReportDao().insert(report(42).toEntityWithResults())
    } finally {
      old.close()
    }
    withContext(Dispatchers.IO) {
      SQLiteDatabase.openDatabase(
          context.getDatabasePath("geoinspect_database").absolutePath,
          null,
          SQLiteDatabase.OPEN_READWRITE,
        )
        .use { sql ->
          sql.execSQL("DROP TABLE report_library_nodes")
          sql.execSQL("DROP TABLE report_library_state")
          sql.version = 7
        }
    }
    val db = opened()
    val library = ReportLibrary(context, db, MoshiFactory.create())
    assertEquals("Keep this content", library.getInspectionReportById(42)!!.content)
    assertEquals(listOf(42L), library.rootIds())
    assertTrue(db.inspectionReportDao().getAllForLibraryMigration().isEmpty())
    library.lockAll()
  }

  @Test
  fun genuineV8LibrarySchemaMigratesToV9WithoutLosingIdsOrObjects() = runBlocking {
    val db = opened()
    seedCompleted(db)
    val library = ReportLibrary(context, db, MoshiFactory.create())
    val id = library.insert(report())
    library.lockAll()
    db.close()
    ReflectionHelpers.setStaticField(AppDatabase::class.java, "INSTANCE", null)
    withContext(Dispatchers.IO) {
      SQLiteDatabase.openDatabase(
          context.getDatabasePath("geoinspect_database").absolutePath,
          null,
          SQLiteDatabase.OPEN_READWRITE,
        )
        .use { sql ->
          sql.execSQL(
            "CREATE TABLE old_nodes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, parentId INTEGER, name TEXT NOT NULL, folder INTEGER NOT NULL, payloadRef TEXT, updatedAt INTEGER NOT NULL, lockId TEXT, salt BLOB, verifier BLOB, iterations INTEGER NOT NULL)"
          )
          sql.execSQL(
            "INSERT INTO old_nodes SELECT id,parentId,name,folder,payloadRef,updatedAt,lockId,salt,verifier,iterations FROM report_library_nodes"
          )
          sql.execSQL("DROP TABLE report_library_nodes")
          sql.execSQL("ALTER TABLE old_nodes RENAME TO report_library_nodes")
          sql.execSQL(
            "CREATE INDEX index_report_library_nodes_parentId ON report_library_nodes(parentId)"
          )
          sql.version = 8
        }
    }
    val reopened = opened()
    val restored = ReportLibrary(context, reopened, MoshiFactory.create())
    assertEquals("Keep this content", restored.getInspectionReportById(id)!!.content)
    assertNull(reopened.libraryDao().all().first { it.id == id }.displayName)
    restored.rename(id, "logical", "Display")
    assertEquals("Display", reopened.libraryDao().all().first { it.id == id }.displayName)
    restored.lockAll()
  }
}
