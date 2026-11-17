package com.karandaev.fieldfolio.core.library

import com.karandaev.fieldfolio.core.security.password.PasswordStrengthCalculator

/**
 * Only newly set passwords are checked; existing protected data keeps accepting its original key.
 */
object LibraryPasswordPolicy {
  fun score(value: String) = PasswordStrengthCalculator.calculate(value)

  fun accepts(value: String): Boolean =
    value.length in 12..1024 &&
      score(value) == 100 &&
      value.lowercase(java.util.Locale.ROOT) !in
        setOf("password123!", "password1234!", "qwerty123456!", "пароль123456!")

  fun requireNew(value: CharArray) {
    require(accepts(value.concatToString())) { "New archive password is too weak" }
  }
}
