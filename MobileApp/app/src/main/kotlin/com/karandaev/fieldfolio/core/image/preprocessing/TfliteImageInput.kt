package com.karandaev.fieldfolio.core.image.preprocessing

import java.io.File
import java.nio.ByteBuffer

/** Prepared TFLite image input. */
data class TfliteImageInput(
  val inputBuffer: ByteBuffer,
  val processedFile: File,
  val transform: LetterboxTransform,
)
