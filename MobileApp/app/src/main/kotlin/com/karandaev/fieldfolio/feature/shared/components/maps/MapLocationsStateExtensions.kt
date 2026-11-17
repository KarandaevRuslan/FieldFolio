package com.karandaev.fieldfolio.feature.shared.components.maps

import com.karandaev.fieldfolio.core.domain.model.location.MapPoint
import com.karandaev.fieldfolio.core.util.mappers.toMapPoint
import com.karandaev.fieldfolio.feature.map.location.LocationResolveMode
import com.karandaev.fieldfolio.feature.map.location.LocationUiState

internal val LocationUiState.currentLocationPoint: MapPoint?
  get() =
    when (this) {
      LocationUiState.Loading -> null
      is LocationUiState.Success -> data.toMapPoint()
      is LocationUiState.Unknown -> null
    }

internal val LocationUiState.isInitialLocationLoading: Boolean
  get() =
    when (this) {
      LocationUiState.Loading -> true

      is LocationUiState.Success -> {
        resolveMode == LocationResolveMode.FullLoading
      }

      is LocationUiState.Unknown -> false
    }
