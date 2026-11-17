package com.karandaev.fieldfolio.feature.auth.state.model

import com.karandaev.fieldfolio.core.auth.model.UserProfile

/** Authentication session state. */
sealed interface AuthSession {

  /** User is not signed in. */
  data object SignedOut : AuthSession

  /**
   * User is signed in.
   *
   * @param profile Current signed-in user profile.
   */
  data class SignedIn(val profile: UserProfile) : AuthSession
}
