package com.karandaev.fieldfolio.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.karandaev.fieldfolio.core.data.local.converter.InspectionModelSourceTypeConverter
import com.karandaev.fieldfolio.core.data.local.converter.InspectionResultTypeConverter
import com.karandaev.fieldfolio.core.data.local.mlmodel.DefaultInspectionModelsSeed
import com.karandaev.fieldfolio.core.data.local.mlmodel.InspectionModelDao
import com.karandaev.fieldfolio.core.data.local.mlmodel.InspectionModelEntity
import com.karandaev.fieldfolio.core.data.local.persisted.PersistedAppStateDao
import com.karandaev.fieldfolio.core.data.local.persisted.PersistedAppStateEntity
import com.karandaev.fieldfolio.core.data.local.report.dao.InspectionReportDao
import com.karandaev.fieldfolio.core.data.local.report.entity.DetectionEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionReportEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionResultEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.SegmentationEntity
import com.karandaev.fieldfolio.core.data.local.settings.AppSettingsEntity
import com.karandaev.fieldfolio.core.data.local.settings.SettingsDao
import com.karandaev.fieldfolio.core.library.LibraryDao
import com.karandaev.fieldfolio.core.library.LibraryNode
import com.karandaev.fieldfolio.core.library.LibraryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val DatabaseName = "geoinspect_database"

/**
 * Local Room database for Fieldfolio.
 *
 * Stores inspection reports, analysis results, app settings, and app runtime state persisted
 * between sessions.
 */
@Database(
  entities =
    [
      InspectionReportEntity::class,
      InspectionResultEntity::class,
      DetectionEntity::class,
      SegmentationEntity::class,
      AppSettingsEntity::class,
      PersistedAppStateEntity::class,
      InspectionModelEntity::class,
      LibraryNode::class,
      LibraryState::class,
    ],
  version = 9,
  exportSchema = true,
)
@TypeConverters(InspectionResultTypeConverter::class, InspectionModelSourceTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

  abstract fun libraryDao(): LibraryDao

  abstract fun inspectionReportDao(): InspectionReportDao

  abstract fun settingsDao(): SettingsDao

  abstract fun persistedAppStateDao(): PersistedAppStateDao

  abstract fun inspectionModelDao(): InspectionModelDao

  companion object {

    val MIGRATION_7_8 =
      object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS report_library_nodes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, parentId INTEGER, name TEXT NOT NULL, folder INTEGER NOT NULL, payloadRef TEXT, updatedAt INTEGER NOT NULL, lockId TEXT, salt BLOB, verifier BLOB, iterations INTEGER NOT NULL)"
          )
          db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_report_library_nodes_parentId ON report_library_nodes(parentId)"
          )
          db.execSQL(
            "CREATE TABLE IF NOT EXISTS report_library_state (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)"
          )
          db.execSQL("INSERT INTO report_library_state (`key`, value) VALUES ('seed', 'false')")
        }
      }

    val MIGRATION_8_9 =
      object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE report_library_nodes ADD COLUMN displayName TEXT")
        }
      }

    @Volatile private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: buildDatabase(context.applicationContext).also { database -> INSTANCE = database }
        }
    }

    private fun buildDatabase(context: Context): AppDatabase {
      return Room.databaseBuilder(context, AppDatabase::class.java, DatabaseName)
        .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
        .addCallback(SeedDatabaseCallback(context))
        .build()
    }
  }

  private class SeedDatabaseCallback(private val context: Context) : Callback() {

    private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onOpen(db: SupportSQLiteDatabase) {
      super.onOpen(db)
      // This PRAGMA returns a result row. execSQL() rejects SQLITE_ROW on Android.
      db.query("PRAGMA secure_delete=ON").use { cursor -> cursor.moveToFirst() }
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
      super.onCreate(db)

      db.execSQL("INSERT INTO report_library_state (`key`, value) VALUES ('seed', 'true')")
      seedScope.launch {
        val database = getDatabase(context)

        // Report seeding is performed atomically by ReportLibrary initialization.
        database.settingsDao().upsert(AppSettingsEntity())

        database.inspectionModelDao().upsertModels(DefaultInspectionModelsSeed.create())
      }
    }
  }
}
