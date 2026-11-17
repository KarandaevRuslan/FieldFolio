package com.karandaev.fieldfolio.core.localization

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.karandaev.fieldfolio.R

/** Adapter for existing domain error strings. Report names and document content never pass here. */
fun Context.localizeMessage(message: String): String {
  messageResources[message]?.let {
    return getString(it)
  }
  for ((pattern, id) in dynamicMessages) {
    val match = pattern.matchEntire(message) ?: continue
    var argument = match.groupValues[1]
    if (id == R.string.message_rate_limit && resources.configuration.locales[0].language == "ru") {
      argument =
        argument
          .replace("minutes", "мин.")
          .replace("minute", "мин.")
          .replace("seconds", "сек.")
          .replace("second", "сек.")
          .replace("min", "мин.")
          .replace("sec", "сек.")
    }
    return getString(id, argument)
  }
  return message
}

@Composable
fun localizedMessage(message: String): String = LocalContext.current.localizeMessage(message)

private val messageResources =
  mapOf(
    "Backend API is not initialized. Set base URL before request." to
      R.string.finish_backend_not_initialized,
    "Continuing as guest." to R.string.message_continuing_as_guest,
    "Signed in successfully." to R.string.message_signed_in_successfully,
    "Signed in with Google successfully." to R.string.message_signed_in_with_google_successfully,
    "You have been signed out." to R.string.message_you_have_been_signed_out,
    "Profile name updated." to R.string.message_profile_name_updated,
    "Password updated." to R.string.message_password_updated,
    "Account deleted." to R.string.message_account_deleted,
    "Profile avatar updated." to R.string.message_profile_avatar_updated,
    "Profile avatar reset." to R.string.message_profile_avatar_reset,
    "Please enter your name." to R.string.message_please_enter_your_name,
    "Please enter your password." to R.string.message_please_enter_your_password,
    "Please enter your email address first." to
      R.string.message_please_enter_your_email_address_first,
    "Please enter your current password." to R.string.message_please_enter_your_current_password,
    "Please select an avatar image first." to R.string.message_please_select_an_avatar_image_first,
    "Please type DELETE to confirm account deletion." to
      R.string.message_please_type_delete_to_confirm_account_deletion,
    "Please enter your new password." to R.string.message_please_enter_your_new_password,
    "Please repeat your new password." to R.string.message_please_repeat_your_new_password,
    "Please enter your email address." to R.string.message_please_enter_your_email_address,
    "Please enter a valid email address." to R.string.message_please_enter_a_valid_email_address,
    "Please repeat your password." to R.string.message_please_repeat_your_password,
    "Passwords do not match." to R.string.message_passwords_do_not_match,
    "Password is not strong enough. Password strength must be 100/100." to
      R.string.message_password_is_not_strong_enough_password_strength_must_be_100_100,
    "Authentication failed. Please try again." to
      R.string.message_authentication_failed_please_try_again,
    "Something went wrong. Please try again." to
      R.string.message_something_went_wrong_please_try_again,
    "Invalid input." to R.string.message_invalid_input,
    "App state is invalid." to R.string.message_app_state_is_invalid,
    "The email address is not valid." to R.string.message_the_email_address_is_not_valid,
    "No account found for this email." to R.string.message_no_account_found_for_this_email,
    "Incorrect password. Please try again." to R.string.message_incorrect_password_please_try_again,
    "Password is too weak. Please choose a stronger password." to
      R.string.message_password_is_too_weak_please_choose_a_stronger_password,
    "Your session expired. Please sign in again." to
      R.string.message_your_session_expired_please_sign_in_again,
    "Your session is no longer valid. Please sign in again." to
      R.string.message_your_session_is_no_longer_valid_please_sign_in_again,
    "This email is already registered. Try signing in instead." to
      R.string.message_this_email_is_already_registered_try_signing_in_instead,
    "Too many attempts. Please wait a bit and try again." to
      R.string.message_too_many_attempts_please_wait_a_bit_and_try_again,
    "Network error. Please check your internet connection." to
      R.string.message_network_error_please_check_your_internet_connection,
    "Please sign in again before changing sensitive account settings." to
      R.string.message_please_sign_in_again_before_changing_sensitive_account_settings,
    "The selected file is empty. Please choose another image." to
      R.string.message_the_selected_file_is_empty_please_choose_another_image,
    "Image is too large. Max size is 10 MB." to
      R.string.message_image_is_too_large_max_size_is_10_mb,
    "Unsupported image format. Please use JPEG, PNG or WEBP." to
      R.string.message_unsupported_image_format_please_use_jpeg_png_or_webp,
    "The image dimensions look invalid. Please choose another image." to
      R.string.message_the_image_dimensions_look_invalid_please_choose_another_image,
    "Image dimensions are too large. Please choose a smaller image." to
      R.string.message_image_dimensions_are_too_large_please_choose_a_smaller_image,
    "Image resolution is too large. Please choose a smaller image." to
      R.string.message_image_resolution_is_too_large_please_choose_a_smaller_image,
    "The image cannot be decoded. Please choose another image." to
      R.string.message_the_image_cannot_be_decoded_please_choose_another_image,
    "The request is invalid. Please check your input and try again." to
      R.string.message_the_request_is_invalid_please_check_your_input_and_try_again,
    "You are not authorized. Please sign in again." to
      R.string.message_you_are_not_authorized_please_sign_in_again,
    "File is too large." to R.string.message_file_is_too_large,
    "Unsupported file format." to R.string.message_unsupported_file_format,
    "Server error. Please try again later." to R.string.message_server_error_please_try_again_later,
    "Service is temporarily unavailable. Please try again later." to
      R.string.message_service_is_temporarily_unavailable_please_try_again_later,
    "Too many requests. Please wait a bit and try again." to
      R.string.message_too_many_requests_please_wait_a_bit_and_try_again,
    "Please select an image first." to R.string.message_please_select_an_image_first,
    "No image selected." to R.string.message_no_image_selected,
    "Camera capture was cancelled." to R.string.message_camera_capture_was_cancelled,
    "Camera permission is required to take a photo." to
      R.string.message_camera_permission_is_required_to_take_a_photo,
    "Detection failed. Please try again." to R.string.message_detection_failed_please_try_again,
    "No connection to the weather service" to R.string.message_no_connection_to_the_weather_service,
    "Invalid response from the weather service" to
      R.string.message_invalid_response_from_the_weather_service,
    "Unknown weather loading error" to R.string.message_unknown_weather_loading_error,
    "Weather location is unavailable" to R.string.message_weather_location_is_unavailable,
    "Location is unknown" to R.string.message_location_is_unknown,
    "Location permission is required to get current location." to
      R.string.message_location_permission_is_required_to_get_current_location,
    "Password change is available only for email/password accounts." to
      R.string.message_password_change_is_available_only_for_email_password_accounts,
    "Reauthentication with password is available only for email/password accounts." to
      R.string.message_reauthentication_with_password_is_available_only_for_email_password_accounts,
    "Current user email is not available." to R.string.message_current_user_email_is_not_available,
    "No signed-in user." to R.string.message_no_signed_in_user,
    "Database reset to defaults." to R.string.message_database_reset_to_defaults,
    "Reset failed" to R.string.message_reset_failed_title,
    "Signed in user" to R.string.message_signed_in_user,
    "Open profile" to R.string.message_open_profile,
    "Use an account to sync your profile" to R.string.message_use_an_account_to_sync_your_profile,
    "Anonymous" to R.string.message_anonymous,
    "Image crop area" to R.string.message_image_crop_area,
    "Image could not be loaded." to R.string.message_image_could_not_be_loaded,
    "Drag to move, pinch to zoom." to R.string.message_drag_to_move_pinch_to_zoom,
  )

private val dynamicMessages =
  listOf(
    Regex(
      "Verification\\ email\\ sent\\ to\\ (.*)\\.\\ Please\\ verify\\ your\\ email,\\ then\\ sign\\ in\\."
    ) to R.string.message_verification_sent,
    Regex(
      "Your\\ email\\ is\\ not\\ verified\\ yet\\.\\ We\\ sent\\ a\\ verification\\ email\\ to\\ (.*)\\.\\ Please\\ verify\\ it\\ and\\ try\\ again\\."
    ) to R.string.message_email_unverified,
    Regex("Password\\ reset\\ email\\ sent\\ to\\ (.*)\\.\\ Please\\ check\\ your\\ inbox\\.") to
      R.string.message_reset_sent,
    Regex("Signed\\ out,\\ but\\ something\\ went\\ wrong\\.\\ (.*)") to
      R.string.message_signout_problem,
    Regex("Request\\ failed\\ \\(HTTP\\ (.*)\\)\\.\\ Please\\ try\\ again\\.") to
      R.string.message_http_failed,
    Regex("Weather\\ server\\ error:\\ (.*)") to R.string.message_weather_server,
    Regex("Weather\\ loading\\ error:\\ (.*)") to R.string.message_weather_failed,
    Regex("Reset\\ failed:\\ (.*)") to R.string.message_reset_failed,
    Regex("Too\\ many\\ requests\\.\\ Please\\ try\\ again\\ in\\ (.*)\\.") to
      R.string.message_rate_limit,
  )
