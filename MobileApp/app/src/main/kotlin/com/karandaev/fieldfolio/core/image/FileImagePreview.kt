package com.karandaev.fieldfolio.core.image

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.*

/** Null means loading; failure is distinct. Crop geometry uses normalized coordinates. */
@Composable
internal fun rememberFileImagePreview(path: String): State<Result<ImageBitmap>?> =
  produceState(null, path) {
    value =
      withContext(Dispatchers.IO) {
        runCatching {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeFile(path, bounds)
          require(bounds.outWidth > 0 && bounds.outHeight > 0)
          var sample = 1
          while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
          requireNotNull(
              BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample },
              )
            )
            .asImageBitmap()
        }
      }
  }
