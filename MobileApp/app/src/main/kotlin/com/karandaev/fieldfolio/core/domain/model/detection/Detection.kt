package com.karandaev.fieldfolio.core.domain.model.detection

/** Detected object with its class label, confidence score, and bounding box. */
data class Detection(val clazz: String, val confidence: Double, val bbox: NormalizedBBox)
