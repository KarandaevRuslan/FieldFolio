package com.karandaev.fieldfolio.feature.profile.components.providers

import androidx.compose.ui.res.stringResource
import com.karandaev.fieldfolio.R

private const val FirebaseProviderId = "firebase"
private const val PasswordProviderId = "password"
private const val GoogleProviderId = "google.com"
private const val PhoneProviderId = "phone"
private const val AnonymousProviderId = "anonymous"

/** Returns user-facing auth provider display name. */
@androidx.compose.runtime.Composable
internal fun String.toAuthProviderDisplayName(): String {
  return when (this) {
    FirebaseProviderId -> "Firebase"
    PasswordProviderId -> stringResource(R.string.ui_email)
    GoogleProviderId -> "Google"
    PhoneProviderId -> stringResource(R.string.ui_phone)
    AnonymousProviderId -> stringResource(R.string.message_anonymous)
    else -> this
  }
}
