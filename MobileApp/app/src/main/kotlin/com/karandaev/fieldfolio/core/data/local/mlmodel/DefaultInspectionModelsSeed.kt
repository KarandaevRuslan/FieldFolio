package com.karandaev.fieldfolio.core.data.local.mlmodel

import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModelSourceType

/** Default bundled inspection models. */
object DefaultInspectionModelsSeed {

  /** Creates default bundled model entities. */
  fun create(): List<InspectionModelEntity> {
    return listOf(
      InspectionModelEntity(
        resultType = InspectionResultType.DETECTION,
        name = "YOLO11s Road Damage INT8",
        sourceType = InspectionModelSourceType.ASSET,
        sourcePath = "models/road_damage_yolo_int8.tflite",
        inputWidth = 640,
        inputHeight = 640,
        description = "Bundled INT8 YOLO model for road damage detection.",
        isQuantized = true,
        isSelected = true,
      )
    )
  }
}
