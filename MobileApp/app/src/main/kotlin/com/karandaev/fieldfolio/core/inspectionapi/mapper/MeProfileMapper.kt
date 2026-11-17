package com.karandaev.fieldfolio.core.inspectionapi.mapper

import com.karandaev.fieldfolio.core.domain.model.profile.MeProfile
import com.karandaev.fieldfolio.core.inspectionapi.dto.profile.MeResponseDto

/** Maps profile DTOs returned by road crack backend to domain models. */
fun MeResponseDto.toMeProfile(): MeProfile {
  return MeProfile(
    uid = uid,
    name = name,
    email = email,
    emailVerified = emailVerified,
    roles = roles,
  )
}
