package com.karandaev.fieldfolio.core.data.repository

import com.karandaev.fieldfolio.core.data.local.mlmodel.DefaultInspectionModelsSeed
import com.karandaev.fieldfolio.core.data.local.mlmodel.InspectionModelDao
import com.karandaev.fieldfolio.core.data.local.mlmodel.toDomain
import com.karandaev.fieldfolio.core.data.local.mlmodel.toEntity
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel
import com.karandaev.fieldfolio.core.domain.repository.InspectionModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local Room-backed implementation of [InspectionModelRepository].
 *
 * This class maps between Room entities and domain models so the rest of the app does not depend on
 * database-specific types.
 */
class LocalInspectionModelRepository(private val inspectionModelDao: InspectionModelDao) :
  InspectionModelRepository {

  // ---------------------------------------------------------------------------
  // Read API
  // ---------------------------------------------------------------------------

  /** Observes all local inspection models. */
  override fun observeModels(): Flow<List<InspectionModel>> {
    return inspectionModelDao.observeModels().map { models ->
      models.map { model -> model.toDomain() }
    }
  }

  /** Observes local inspection models by result type. */
  override fun observeModelsByType(resultType: InspectionResultType): Flow<List<InspectionModel>> {
    return inspectionModelDao.observeModelsByResultType(resultType = resultType).map { models ->
      models.map { model -> model.toDomain() }
    }
  }

  /** Returns a model by id, or null if it does not exist. */
  override suspend fun getModel(id: Long): InspectionModel? {
    return inspectionModelDao.getModel(id = id)?.toDomain()
  }

  /** Returns selected model by result type. */
  override suspend fun getSelectedModelByType(resultType: InspectionResultType): InspectionModel? {
    return inspectionModelDao.getSelectedModelByResultType(resultType = resultType)?.toDomain()
  }

  // ---------------------------------------------------------------------------
  // Defaults
  // ---------------------------------------------------------------------------

  /** Ensures that default bundled models exist. */
  override suspend fun ensureDefaults() {
    DefaultInspectionModelsSeed.create().forEach { defaultModel ->
      val existingModel =
        inspectionModelDao.getModelBySource(
          sourceType = defaultModel.sourceType,
          sourcePath = defaultModel.sourcePath,
        )

      if (existingModel == null) {
        inspectionModelDao.upsertModel(defaultModel)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Write API
  // ---------------------------------------------------------------------------

  /** Inserts or replaces a local inspection model. */
  override suspend fun upsertModel(model: InspectionModel): Long {
    return inspectionModelDao.upsertModel(
      model = model.copy(updatedAtMillis = System.currentTimeMillis()).toEntity()
    )
  }

  /** Selects model as active within its result type. */
  override suspend fun selectModel(id: Long) {
    inspectionModelDao.selectModel(id = id)
  }

  /** Deletes model by id. */
  override suspend fun deleteModel(id: Long) {
    inspectionModelDao.deleteModel(id = id)
  }
}
