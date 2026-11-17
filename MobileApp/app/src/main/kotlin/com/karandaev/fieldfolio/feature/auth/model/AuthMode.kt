package com.karandaev.fieldfolio.feature.auth.model

/** Authentication screen mode. */
enum class AuthMode {

  /** Existing user sign-in. */
  Login,

  /** New user registration. */
  Register,

  /** Password reset by email. */
  PasswordReset,
}
