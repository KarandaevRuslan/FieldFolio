package com.karandaev.fieldfolio.core.library

import android.content.Context
import androidx.room.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "report_library_nodes", indices = [Index("parentId")])
data class LibraryNode(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val parentId: Long? = null,
  val name: String,
  val folder: Boolean,
  val payloadRef: String? = null,
  val updatedAt: Long = System.currentTimeMillis(),
  val lockId: String? = null,
  val salt: ByteArray? = null,
  val verifier: ByteArray? = null,
  val iterations: Int = LibraryCrypto.ITERATIONS,
  val displayName: String? = null,
)

@Entity(tableName = "report_library_state")
data class LibraryState(@PrimaryKey val key: String, val value: String)

@Dao
interface LibraryDao {
  @Query("SELECT * FROM report_library_nodes ORDER BY id") fun observe(): Flow<List<LibraryNode>>

  @Query("SELECT * FROM report_library_nodes ORDER BY id") suspend fun all(): List<LibraryNode>

  @Insert suspend fun insert(node: LibraryNode): Long

  @Update suspend fun update(node: LibraryNode)

  @Query("DELETE FROM report_library_nodes WHERE id IN (:ids)") suspend fun delete(ids: List<Long>)

  @Query("SELECT value FROM report_library_state WHERE `key` = :key")
  suspend fun state(key: String): String?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun state(value: LibraryState)
}

/** Immutable revisions: a failed database transaction cannot damage the previous payload. */
internal class LibraryObjects(context: Context) {
  private val root = File(context.filesDir, "report-library/objects").apply { mkdirs() }

  private fun file(ref: String): File {
    require(ref.matches(Regex("[a-f0-9-]{36}\\.bin"))) { "Invalid object reference" }
    return File(root, ref)
  }

  fun input(ref: String) = file(ref).inputStream()

  fun size(ref: String) = file(ref).length()

  fun read(ref: String): ByteArray =
    file(ref).inputStream().use { LibraryZip.read(it, LibraryZip.MAX_OBJECT) }

  fun write(bytes: ByteArray): String {
    require(bytes.size <= LibraryZip.MAX_OBJECT)
    val ref = "${UUID.randomUUID()}.bin"
    val target = file(ref)
    try {
      FileOutputStream(target).use {
        it.write(bytes)
        it.fd.sync()
      }
    } catch (e: Exception) {
      target.delete()
      throw e
    }
    return ref
  }

  fun delete(ref: String?) {
    if (ref != null) file(ref).delete()
  }

  fun cleanup(used: Set<String>) {
    root.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
  }
}

class LibraryLockedException :
  IllegalStateException("Unlock the selected folder and its protected contents first")

class LibraryPasswordException :
  IllegalArgumentException("Incorrect password or damaged encrypted file")

class LibraryConflictException : IllegalArgumentException("An item with this name already exists")

val LibraryNode.displayLabel: String
  get() = displayName?.takeIf { it.isNotBlank() } ?: name
