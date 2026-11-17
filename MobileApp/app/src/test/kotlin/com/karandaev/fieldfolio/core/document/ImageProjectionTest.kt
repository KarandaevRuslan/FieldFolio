package com.karandaev.fieldfolio.core.document

import android.graphics.*
import com.karandaev.fieldfolio.core.image.crop.*
import com.karandaev.fieldfolio.core.models.ModelExecution
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageProjectionTest {
  private fun png(bitmap: Bitmap) =
    ByteArrayOutputStream()
      .also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      .toByteArray()
      .also { bitmap.recycle() }

  private fun image() =
    png(
      Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLACK)
        Canvas(this).drawRect(20f, 10f, 180f, 90f, Paint().apply { color = Color.RED })
      }
    )

  private fun result() =
    ModelResultData(
      modelId = "test",
      modelName = "Model",
      provider = "LOCAL",
      task = "DETECTION",
      boxes = listOf(ResultBox("target", .9, .25, .25, .75, .75)),
      scores = listOf(ResultScore("unchanged", .8)),
      values = listOf(ResultValue("regression", 4.0)),
    )

  @Test
  fun trimFindsRectangularMatteWithoutChangingOriginalBytes() = runBlocking {
    val source = image()
    val copy = source.copyOf()
    val bounds = uniformBorderBounds(source)!!
    assertEquals(NormalizedCropBox(.1, .1, .9, .9), bounds)
    assertArrayEquals(copy, source)
    val cropped = renderFrame(source, bounds)
    assertEquals(160, cropped.width)
    assertEquals(80, cropped.height)
    assertArrayEquals(source, renderFrame(source, FullImageBounds).bytes)
  }

  @Test
  fun aFullBleedOrUniformImageIsNotDestroyed() = runBlocking {
    val all =
      png(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) })
    assertNull(uniformBorderBounds(all))
    val textured =
      png(
        Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
          for (y in 0 until height) for (x in 0 until width) setPixel(
            x,
            y,
            if ((x + y) % 2 == 0) Color.RED else Color.BLUE,
          )
        }
      )
    assertNull(uniformBorderBounds(textured))
  }

  @Test
  fun sourceAndTrimmedBaseHaveIndependentResetOperations() = runBlocking {
    val state = ImageFrameState()
    state.installOwned(image())
    val original = state.source!!.copyOf()
    val trimmed = state.prepare(NormalizedCropBox(.1, .1, .9, .9))
    state.commit(trimmed, trimmed.bounds)
    state.commit(state.prepare(NormalizedCropBox(.25, .25, .75, .75).inside(state.base)))
    assertEquals(NormalizedCropBox(.3, .3, .7, .7), state.bounds)
    state.commit(state.prepare(state.base))
    assertArrayEquals(trimmed.bytes, state.bytes)
    state.commit(state.prepare(FullImageBounds), FullImageBounds)
    assertArrayEquals(original, state.bytes)
    state.clear()
    assertNull(state.source)
  }

  @Test
  fun detectionReprojectsFromRunFrameAndCanRestoreWithoutRerun() {
    val run = NormalizedCropBox(.25, 0.0, .75, 1.0)
    val canonical = result()
    val projected = projectResult(canonical, run, FullImageBounds)
    assertEquals(.375, projected.boxes.single().xMin, .00001)
    assertEquals(.625, projected.boxes.single().xMax, .00001)
    assertEquals(canonical.scores, projected.scores)
    assertEquals(canonical.values, projected.values)
    assertSame(canonical, projectResult(canonical, run, run))
    assertEquals(.25, canonical.boxes.single().xMin, 0.0)
  }

  @Test
  fun boxesOutsideTheNewCropDisappearButCanonicalRemainsUntouched() {
    val canonical =
      result().copy(boxes = result().boxes + ResultBox("outside", .5, 0.0, 0.0, .1, .1))
    val view = NormalizedCropBox(.5, .5, 1.0, 1.0)
    val output = projectResult(canonical, FullImageBounds, view)
    assertEquals(1, output.boxes.size)
    assertEquals(ResultBox("target", .9, 0.0, 0.0, .5, .5), output.boxes.single())
    assertEquals(2, canonical.boxes.size)
  }

  @Test
  fun vectorSegmentationClipsPolygonsAndRetainsAnInteriorHole() {
    val outer =
      listOf(
        NormalizedPoint(.1, .1),
        NormalizedPoint(.9, .1),
        NormalizedPoint(.9, .9),
        NormalizedPoint(.1, .9),
      )
    val hole =
      listOf(
        NormalizedPoint(.4, .4),
        NormalizedPoint(.6, .4),
        NormalizedPoint(.6, .6),
        NormalizedPoint(.4, .6),
      )
    val canonical =
      result()
        .copy(
          layers =
            listOf(
              SegmentationLayer("mask", polygons = listOf(SegmentationPolygon(listOf(outer, hole))))
            )
        )
    val projected = projectResult(canonical, FullImageBounds, NormalizedCropBox(.25, .25, .75, .75))
    val rings = projected.layers.single().polygons.single().rings
    assertEquals(2, rings.size)
    assertTrue(rings.flatten().all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
    assertEquals(.3, rings[1][0].x, .00001)
  }

  @Test
  fun rasterSegmentationGetsTransparentMarginsWhenRestoringTheSource() = runBlocking {
    val mask =
      png(Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
    val canonical = ModelExecution(result().copy(maskAsset = "mask.png"), mapOf("mask.png" to mask))
    val projected =
      projectExecution(
        canonical,
        NormalizedCropBox(.25, .25, .75, .75),
        ImageFrame(image(), FullImageBounds, 200, 100),
      )
    val bitmap =
      BitmapFactory.decodeByteArray(
        projected.files["mask.png"]!!,
        0,
        projected.files["mask.png"]!!.size,
      )
    assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    assertEquals(255, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
    bitmap.recycle()
    assertArrayEquals(mask, canonical.files["mask.png"])
  }
}
