package com.karandaev.fieldfolio.core.data.local.report.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.karandaev.fieldfolio.core.data.local.report.compatibility.InspectionResultCompatibility.DetectionResultDescription
import com.karandaev.fieldfolio.core.data.local.report.compatibility.InspectionResultCompatibility.DetectionResultTitle
import com.karandaev.fieldfolio.core.data.local.report.entity.DetectionEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionReportEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.InspectionResultEntity
import com.karandaev.fieldfolio.core.data.local.report.entity.SegmentationEntity
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionReportWithResults
import com.karandaev.fieldfolio.core.data.local.report.relation.InspectionResultWithPayload
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import kotlinx.coroutines.flow.Flow

/** DAO for accessing inspection reports in the local database. */
@Dao
interface InspectionReportDao {
  @Transaction
  @Query("SELECT * FROM inspection_reports ORDER BY id")
  suspend fun getAllForLibraryMigration(): List<InspectionReportWithResults>

  @Query("DELETE FROM inspection_reports") suspend fun clearMigratedReports()

  // ---------------------------------------------------------------------------
  // Read API
  // ---------------------------------------------------------------------------

  /** Observes all reports ordered by last update time. */
  @Transaction
  @Query("SELECT * FROM inspection_reports ORDER BY lastUpdatedAtMillis DESC")
  fun observeInspectionReports(): Flow<List<InspectionReportWithResults>>

  /** Observes a report by id. */
  @Transaction
  @Query("SELECT * FROM inspection_reports WHERE id = :id")
  fun observeInspectionReportById(id: Long): Flow<InspectionReportWithResults?>

  /** Returns a report by id. */
  @Transaction
  @Query("SELECT * FROM inspection_reports WHERE id = :id")
  suspend fun getInspectionReportById(id: Long): InspectionReportWithResults?

  // ---------------------------------------------------------------------------
  // Public write API
  // ---------------------------------------------------------------------------

  /** Inserts a report with all its analysis results. */
  @Transaction
  suspend fun insert(inspectionReport: InspectionReportWithResults): Long {
    val reportId = insertReport(inspectionReport.report)

    replaceResults(inspectionReportId = reportId, results = inspectionReport.results)

    return reportId
  }

  /** Inserts multiple reports with all their analysis results. */
  @Transaction
  suspend fun insertAll(inspectionReports: List<InspectionReportWithResults>): List<Long> {
    return inspectionReports.map { inspectionReport -> insert(inspectionReport) }
  }

  /**
   * Full local update.
   *
   * Replaces all analysis results for the inspection report. Do not use this from the current flat
   * domain compatibility layer.
   */
  @Transaction
  suspend fun update(inspectionReport: InspectionReportWithResults) {
    updateReport(inspectionReport.report)

    replaceResults(
      inspectionReportId = inspectionReport.report.id,
      results = inspectionReport.results,
    )
  }

  /** Deletes a report by id. */
  @Query("DELETE FROM inspection_reports WHERE id = :id") suspend fun deleteById(id: Long)

  /**
   * Deletes all reports.
   *
   * Results, detections, and segmentations are deleted automatically by foreign key cascade.
   */
  @Query("DELETE FROM inspection_reports") suspend fun deleteAll()

  /** Replaces all reports in one transaction. */
  @Transaction
  suspend fun replaceAll(inspectionReports: List<InspectionReportWithResults>) {
    deleteAll()
    insertAll(inspectionReports)
  }

  // ---------------------------------------------------------------------------
  // Flat domain compatibility API
  // ---------------------------------------------------------------------------

  /**
   * Compatibility update for the old flat domain model.
   *
   * Updates inspection report fields and only the first DETECTION result payload. Existing
   * SEGMENTATION results and additional results are preserved.
   */
  @Transaction
  suspend fun updateFlatDomainReport(
    inspectionReport: InspectionReportEntity,
    detections: List<DetectionEntity>,
  ) {
    updateReport(inspectionReport)

    updateDetectResponse(inspectionReportId = inspectionReport.id, detections = detections)
  }

  /**
   * Compatibility method for old flat DetectResponse.
   *
   * Replaces detections only inside the first DETECTION result. Other results are preserved.
   */
  @Transaction
  suspend fun updateDetectResponse(inspectionReportId: Long, detections: List<DetectionEntity>) {
    val resultId =
      getFirstDetectionResultId(inspectionReportId = inspectionReportId)
        ?: run {
          if (detections.isEmpty()) {
            return
          }

          insertResult(
            InspectionResultEntity(
              inspectionReportId = inspectionReportId,
              sortOrder = 0,
              title = DetectionResultTitle,
              description = DetectionResultDescription,
              type = InspectionResultType.DETECTION,
            )
          )
        }

    replaceDetections(inspectionResultId = resultId, detections = detections)
  }

  // ---------------------------------------------------------------------------
  // Entity insert/update operations
  // ---------------------------------------------------------------------------

  /** Inserts only the inspection report entity. */
  @Insert(onConflict = OnConflictStrategy.Companion.ABORT)
  suspend fun insertReport(inspectionReport: InspectionReportEntity): Long

  /** Inserts one analysis result entity. */
  @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
  suspend fun insertResult(result: InspectionResultEntity): Long

  /** Inserts detection entities. */
  @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
  suspend fun insertDetections(detections: List<DetectionEntity>): List<Long>

  /** Inserts segmentation entities. */
  @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
  suspend fun insertSegmentations(segmentations: List<SegmentationEntity>): List<Long>

  /** Updates only the inspection report entity. */
  @Update suspend fun updateReport(inspectionReport: InspectionReportEntity)

  // ---------------------------------------------------------------------------
  // Result lookup queries
  // ---------------------------------------------------------------------------

  /** Returns the first DETECTION result for flat domain compatibility. */
  @Query(
    """
    SELECT id
    FROM inspection_results
    WHERE inspectionReportId = :inspectionReportId
      AND type = 'DETECTION'
    ORDER BY sortOrder ASC, id ASC
    LIMIT 1
    """
  )
  suspend fun getFirstDetectionResultId(inspectionReportId: Long): Long?

  // ---------------------------------------------------------------------------
  // Delete queries
  // ---------------------------------------------------------------------------

  /** Deletes all analysis results for the provided inspection report. */
  @Query(
    """
    DELETE FROM inspection_results
    WHERE inspectionReportId = :inspectionReportId
    """
  )
  suspend fun deleteResultsByInspectionReportId(inspectionReportId: Long)

  /** Deletes detections for the provided inspection result. */
  @Query(
    """
    DELETE FROM inspection_result_detections
    WHERE inspectionResultId = :inspectionResultId
    """
  )
  suspend fun deleteDetectionsByInspectionResultId(inspectionResultId: Long)

  /** Deletes segmentations for the provided inspection result. */
  @Query(
    """
    DELETE FROM inspection_result_segmentations
    WHERE inspectionResultId = :inspectionResultId
    """
  )
  suspend fun deleteSegmentationsByInspectionResultId(inspectionResultId: Long)

  // ---------------------------------------------------------------------------
  // Replace helpers
  // ---------------------------------------------------------------------------

  /** Replaces all analysis results for the provided inspection report. */
  @Transaction
  suspend fun replaceResults(inspectionReportId: Long, results: List<InspectionResultWithPayload>) {
    deleteResultsByInspectionReportId(inspectionReportId)

    results
      .sortedBy { resultWithPayload -> resultWithPayload.result.sortOrder }
      .forEachIndexed { index, resultWithPayload ->
        val result =
          resultWithPayload.result.copy(
            id = 0,
            inspectionReportId = inspectionReportId,
            sortOrder = index,
          )

        val resultId = insertResult(result)

        when (result.type) {
          InspectionResultType.DETECTION -> {
            replaceDetections(
              inspectionResultId = resultId,
              detections = resultWithPayload.detections,
            )
          }

          InspectionResultType.SEGMENTATION -> {
            replaceSegmentations(
              inspectionResultId = resultId,
              segmentations = resultWithPayload.segmentations,
            )
          }
        }
      }
  }

  /** Replaces all detections for the provided DETECTION result. */
  @Transaction
  suspend fun replaceDetections(inspectionResultId: Long, detections: List<DetectionEntity>) {
    deleteDetectionsByInspectionResultId(inspectionResultId)

    if (detections.isNotEmpty()) {
      insertDetections(
        detections.map { detection ->
          detection.copy(id = 0, inspectionResultId = inspectionResultId)
        }
      )
    }
  }

  /** Replaces all segmentations for the provided SEGMENTATION result. */
  @Transaction
  suspend fun replaceSegmentations(
    inspectionResultId: Long,
    segmentations: List<SegmentationEntity>,
  ) {
    deleteSegmentationsByInspectionResultId(inspectionResultId)

    if (segmentations.isNotEmpty()) {
      insertSegmentations(
        segmentations.map { segmentation ->
          segmentation.copy(id = 0, inspectionResultId = inspectionResultId)
        }
      )
    }
  }
}
