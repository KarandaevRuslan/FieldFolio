package com.karandaev.fieldfolio.core.inspectionapi

import com.karandaev.fieldfolio.core.domain.model.profile.MeProfile

/** Provides road crack inspection backend operations. */
interface InspectionProvider : DetectionProvider {
  /** Uploads an in-memory image without writing decrypted report resources to disk. */
  suspend fun detectBytes(
    bytes: ByteArray
  ): Result<com.karandaev.fieldfolio.core.domain.model.detection.DetectResponse> =
    Result.failure(UnsupportedOperationException("In-memory detection is unavailable"))

  /**
   * Checks if base url is valid.
   *
   * @param baseUrl Backend base URL for validation.
   */
  fun isValidBaseUrl(baseUrl: String): Boolean

  /**
   * Updates backend base URL used by remote road crack API.
   *
   * @param baseUrl New backend base URL.
   */
  fun updateBaseUrl(baseUrl: String)

  /** Clears backend base URL and disables remote API until a new URL is set. */
  fun clearBaseUrl()

  /**
   * Checks whether backend server is reachable using the provided base URL without changing
   * currently configured backend API.
   *
   * @param baseUrl Backend base URL to check.
   */
  suspend fun checkServerHealth(baseUrl: String): Result<Boolean>

  /** Checks whether configured backend server is reachable. */
  suspend fun checkServerHealth(): Result<Boolean>

  /** Returns current authenticated backend user. */
  suspend fun me(): Result<MeProfile>

  /** Returns current user's avatar URL, or null if no custom avatar is set. */
  suspend fun getAvatarUrl(): Result<String?>

  /**
   * Uploads or replaces current user's avatar.
   *
   * @param sourcePath Absolute or relative path to the local avatar image file.
   */
  suspend fun updateAvatar(sourcePath: String): Result<String?>

  /** Resets current user's avatar to default. */
  suspend fun resetAvatar(): Result<String?>

  /**
   * Downloads an avatar image by direct public URL.
   *
   * @param avatarUrl Public avatar URL returned by the backend.
   * @return Raw avatar image bytes.
   */
  suspend fun downloadAvatar(avatarUrl: String): Result<ByteArray>

  /**
   * Gets current user's avatar URL and downloads avatar image bytes.
   *
   * @return Raw avatar image bytes, or null if no custom avatar is set.
   */
  suspend fun downloadCurrentAvatar(): Result<ByteArray?>
}
