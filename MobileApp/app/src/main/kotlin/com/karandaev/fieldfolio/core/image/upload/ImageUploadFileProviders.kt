package com.karandaev.fieldfolio.core.image.upload

import com.karandaev.fieldfolio.core.image.compression.JpegImageFileCompressor
import com.karandaev.fieldfolio.core.image.validation.DefaultImageValidationProvider

/** Factory methods for upload file providers used by road crack backend endpoints. */
object ImageUploadFileProviders {

  /**
   * Upload provider for road crack detection images.
   *
   * Keeps the existing backend-oriented 10 MB limit.
   */
  fun detect(): ImageUploadFileProvider {
    return DefaultImageUploadFileProvider(
      fileCompressor =
        JpegImageFileCompressor(quality = 85, targetImageSizePx = 768, isLetterboxEnabled = true),
      imageValidationProvider = DefaultImageValidationProvider(),
      maxFileSizeBytes = 10 * 1024 * 1024,
      formFieldName = "file",
    )
  }

  /**
   * Upload provider for profile avatars.
   *
   * Uses smaller output because avatars do not need detection-level resolution.
   */
  fun avatar(): ImageUploadFileProvider {
    return DefaultImageUploadFileProvider(
      fileCompressor = JpegImageFileCompressor(quality = 85, targetImageSizePx = 512),
      imageValidationProvider = DefaultImageValidationProvider(),
      maxFileSizeBytes = 5 * 1024 * 1024,
      formFieldName = "file",
    )
  }
}
