package com.karandaev.fieldfolio.feature.map.state

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.feature.map.location.LocationUiState

fun LocationUiState.toMapPointOrNull(): MapPoint? {
  return when (this) {
    LocationUiState.Loading -> null

    is LocationUiState.Success -> {
      MapPoint(latitude = data.latitude, longitude = data.longitude)
    }

    is LocationUiState.Unknown -> null
  }
}
