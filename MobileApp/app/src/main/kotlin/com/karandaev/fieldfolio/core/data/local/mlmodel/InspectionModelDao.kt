package com.karandaev.fieldfolio.core.data.local.mlmodel

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModelSourceType
import kotlinx.coroutines.flow.Flow

/** DAO for accessing local inspection ML models. */
@Dao
interface InspectionModelDao {

  // ---------------------------------------------------------------------------
  // Read API
  // ---------------------------------------------------------------------------

  /** Observes all local inspection models. */
  @Query("SELECT * FROM inspection_models ORDER BY is_selected DESC, name ASC")
  fun observeModels(): Flow<List<InspectionModelEntity>>

  /** Observes local inspection models by result type. */
  @Query(
    """
    SELECT *
    FROM inspection_models
    WHERE result_type = :resultType
    ORDER BY is_selected DESC, name ASC
    """
  )
  fun observeModelsByResultType(resultType: InspectionResultType): Flow<List<InspectionModelEntity>>

  /** Returns a local inspection model by id. */
  @Query("SELECT * FROM inspection_models WHERE id = :id LIMIT 1")
  suspend fun getModel(id: Long): InspectionModelEntity?

  /** Returns currently selected model by result type. */
  @Query(
    """
    SELECT *
    FROM inspection_models
    WHERE result_type = :resultType
      AND is_selected = 1
    LIMIT 1
    """
  )
  suspend fun getSelectedModelByResultType(resultType: InspectionResultType): InspectionModelEntity?

  /** Returns a model entity by source. */
  @Query(
    """
    SELECT *
    FROM inspection_models
    WHERE source_type = :sourceType
      AND source_path = :sourcePath
    LIMIT 1
    """
  )
  suspend fun getModelBySource(
    sourceType: InspectionModelSourceType,
    sourcePath: String,
  ): InspectionModelEntity?

  /** Returns model result type by model id. */
  @Query("SELECT result_type FROM inspection_models WHERE id = :id LIMIT 1")
  suspend fun getResultTypeByModelId(id: Long): InspectionResultType?

  // ---------------------------------------------------------------------------
  // Write API
  // ---------------------------------------------------------------------------

  /** Inserts or replaces a local inspection model. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertModel(model: InspectionModelEntity): Long

  /** Inserts or replaces local inspection models. */
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertModels(models: List<InspectionModelEntity>)

  /** Deletes model by id. */
  @Query("DELETE FROM inspection_models WHERE id = :id") suspend fun deleteModel(id: Long)

  // ---------------------------------------------------------------------------
  // Selection API
  // ---------------------------------------------------------------------------

  /** Clears selected model flag for all models of the provided result type. */
  @Query(
    """
    UPDATE inspection_models
    SET is_selected = 0,
        updated_at_millis = :updatedAtMillis
    WHERE result_type = :resultType
    """
  )
  suspend fun clearSelectedModelsByResultType(
    resultType: InspectionResultType,
    updatedAtMillis: Long,
  )

  /** Updates selected model flag. */
  @Query(
    """
    UPDATE inspection_models
    SET is_selected = :isSelected,
        updated_at_millis = :updatedAtMillis
    WHERE id = :id
    """
  )
  suspend fun setModelSelected(id: Long, isSelected: Boolean, updatedAtMillis: Long)

  /** Selects one model within its result type. */
  @Transaction
  suspend fun selectModel(id: Long) {
    val resultType =
      getResultTypeByModelId(id) ?: error("Inspection model with id=$id was not found.")

    val now = System.currentTimeMillis()

    clearSelectedModelsByResultType(resultType = resultType, updatedAtMillis = now)

    setModelSelected(id = id, isSelected = true, updatedAtMillis = now)
  }
}
