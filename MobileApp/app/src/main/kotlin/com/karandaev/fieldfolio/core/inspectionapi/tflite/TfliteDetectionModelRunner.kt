package com.karandaev.fieldfolio.core.inspectionapi.tflite

import android.content.Context
import com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse
import com.karandaev.fieldfolio.core.domain.model.inspection.InspectionResultType
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModel
import com.karandaev.fieldfolio.core.domain.model.mlmodel.InspectionModelSourceType
import com.karandaev.fieldfolio.core.image.preprocessing.TfliteImagePreprocessor
import com.karandaev.fieldfolio.core.inspectionapi.DetectionModelRunner
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter

private const val DefaultThreadCount = 4
private const val BatchSize = 1
private const val BoxValuesCount = 4

/**
 * TFLite implementation of [DetectionModelRunner].
 *
 * This runner:
 * - validates source image;
 * - resizes and compresses it to exactly model.inputWidth x model.inputHeight;
 * - runs TensorFlow Lite inference;
 * - parses YOLO-like output into domain [DetectResponse].
 *
 * @param context Android context used for loading asset models.
 * @param classLabels Labels in the same order as model class output.
 * @param imagePreprocessor Image preprocessor for fixed-size model input.
 * @param confidenceThreshold Minimum class confidence.
 * @param iouThreshold NMS IoU threshold.
 * @param threadCount TFLite interpreter thread count.
 */
class TfliteDetectionModelRunner(
  context: Context,
  private val classLabels: List<String>,
  private val imagePreprocessor: TfliteImagePreprocessor = TfliteImagePreprocessor(),
  private val confidenceThreshold: Float = 0.25f,
  private val iouThreshold: Float = 0.45f,
  private val threadCount: Int = DefaultThreadCount,
) : DetectionModelRunner {

  private val appContext = context.applicationContext

  private val outputParser =
    YoloTfliteOutputParser(
      classLabels = classLabels,
      confidenceThreshold = confidenceThreshold,
      iouThreshold = iouThreshold,
    )

  override suspend fun detect(sourcePath: String, model: InspectionModel): DetectResponse {
    return withContext(Dispatchers.Default) {
      validateRequest(sourcePath = sourcePath, model = model)

      val imageInput =
        imagePreprocessor.preprocess(
          sourcePath = sourcePath,
          targetWidth = model.inputWidth,
          targetHeight = model.inputHeight,
          isQuantized = model.isQuantized,
        )

      val modelBuffer = loadModel(model = model)

      Interpreter(modelBuffer, Interpreter.Options().setNumThreads(threadCount)).use { interpreter
        ->
        validateInterpreterInput(interpreter = interpreter, model = model)

        val output = createOutputBuffer(interpreter = interpreter)

        interpreter.run(imageInput.inputBuffer, output)

        DetectResponse(
          detections = outputParser.parse(output = output, transform = imageInput.transform)
        )
      }
    }
  }

  private fun validateRequest(sourcePath: String, model: InspectionModel) {
    require(sourcePath.isNotBlank()) { "Source path must not be blank." }

    require(model.resultType == InspectionResultType.DETECTION) {
      "TFLite detection runner requires resultType=${InspectionResultType.DETECTION}, but was ${model.resultType}."
    }

    require(model.inputWidth > 0) { "Model inputWidth must be greater than 0." }

    require(model.inputHeight > 0) { "Model inputHeight must be greater than 0." }

    require(classLabels.isNotEmpty()) { "Class labels must not be empty." }

    require(threadCount > 0) { "Thread count must be greater than 0." }
  }

  private fun validateInterpreterInput(interpreter: Interpreter, model: InspectionModel) {
    val inputTensor = interpreter.getInputTensor(0)
    val shape = inputTensor.shape()

    require(shape.size == 4) {
      "Expected model input shape [1, height, width, channels], but was ${shape.contentToString()}."
    }

    require(shape[0] == BatchSize) { "Expected batch size $BatchSize, but was ${shape[0]}." }

    require(shape[1] == model.inputHeight) {
      "Model inputHeight=${model.inputHeight}, but TFLite input height is ${shape[1]}."
    }

    require(shape[2] == model.inputWidth) {
      "Model inputWidth=${model.inputWidth}, but TFLite input width is ${shape[2]}."
    }

    require(shape[3] == 3) { "Expected RGB input with 3 channels, but was ${shape[3]}." }
  }

  private fun createOutputBuffer(interpreter: Interpreter): Array<Array<FloatArray>> {
    val outputTensor = interpreter.getOutputTensor(0)
    val shape = outputTensor.shape()

    require(shape.size == 3) {
      "Expected YOLO output shape [1, candidates, values] or [1, values, candidates], but was ${shape.contentToString()}."
    }

    require(shape[0] == BatchSize) { "Expected output batch size $BatchSize, but was ${shape[0]}." }

    val firstDim = shape[1]
    val secondDim = shape[2]

    val minExpectedValues = BoxValuesCount + classLabels.size

    require(firstDim >= minExpectedValues || secondDim >= minExpectedValues) {
      "Output shape ${shape.contentToString()} does not contain enough values for ${classLabels.size} classes."
    }

    return Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
  }

  private fun loadModel(model: InspectionModel): MappedByteBuffer {
    return when (model.sourceType) {
      InspectionModelSourceType.ASSET -> {
        loadAssetModel(assetPath = model.sourcePath)
      }

      InspectionModelSourceType.FILE -> {
        loadFileModel(filePath = model.sourcePath)
      }
    }
  }

  private fun loadAssetModel(assetPath: String): MappedByteBuffer {
    require(assetPath.isNotBlank()) { "Asset model path must not be blank." }

    val assetFileDescriptor = appContext.assets.openFd(assetPath)

    return assetFileDescriptor.use { descriptor ->
      FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
        channel.map(
          FileChannel.MapMode.READ_ONLY,
          descriptor.startOffset,
          descriptor.declaredLength,
        )
      }
    }
  }

  private fun loadFileModel(filePath: String): MappedByteBuffer {
    require(filePath.isNotBlank()) { "Model file path must not be blank." }

    val file = File(filePath)

    require(file.exists()) { "Model file does not exist." }

    require(file.isFile) { "Model path does not point to a file." }

    return FileInputStream(file).channel.use { channel ->
      channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    }
  }
}
