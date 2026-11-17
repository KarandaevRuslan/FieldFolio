package com.karandaev.fieldfolio.core.domain.repository

import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel
import kotlinx.coroutines.flow.Flow

/** Repository for accessing local inspection ML models. */
interface InspectionModelRepository {

  /** Observes all local inspection models. */
  fun observeModels(): Flow<List<InspectionModel>>

  /** Observes local inspection models by result type. */
  fun observeModelsByType(resultType: InspectionResultType): Flow<List<InspectionModel>>

  /** Ensures that default bundled models exist. */
  suspend fun ensureDefaults()

  /** Returns a model by id, or null if it does not exist. */
  suspend fun getModel(id: Long): InspectionModel?

  /** Returns selected model by result type. */
  suspend fun getSelectedModelByType(resultType: InspectionResultType): InspectionModel?

  /** Inserts or replaces a local inspection model. */
  suspend fun upsertModel(model: InspectionModel): Long

  /** Selects model as active within its result type. */
  suspend fun selectModel(id: Long)

  /** Deletes model by id. */
  suspend fun deleteModel(id: Long)
}
