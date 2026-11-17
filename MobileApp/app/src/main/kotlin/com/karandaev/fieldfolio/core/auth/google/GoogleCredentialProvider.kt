package com.karandaev.fieldfolio.core.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.karandaev.fieldfolio.R

/** Provides Google ID tokens using Android Credential Manager. */
class GoogleCredentialProvider(
  private val context: Context,
  private val credentialManager: CredentialManager = CredentialManager.create(context),
) {

  /**
   * Requests a Google ID token for Firebase authentication.
   *
   * @return Google ID token selected by the user.
   */
  suspend fun getIdToken(): String {
    val webClientId = configuredWebClientId(context)

    val googleIdOption =
      GetGoogleIdOption.Builder()
        .setServerClientId(webClientId)
        .setFilterByAuthorizedAccounts(false)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    val result = credentialManager.getCredential(context = context, request = request)

    val credential = GoogleIdTokenCredential.createFrom(result.credential.data)

    return credential.idToken
  }
}

/**
 * Resource is optional in source-only builds; valid Google/Firebase credentials are used unchanged.
 */
@android.annotation.SuppressLint("DiscouragedApi")
internal fun configuredWebClientId(context: Context): String {
  val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
  check(id != 0) { context.getString(R.string.v20_google_unavailable) }
  return context.getString(id).trim().also {
    check(it.isNotBlank()) { context.getString(R.string.v20_google_unavailable) }
  }
}
