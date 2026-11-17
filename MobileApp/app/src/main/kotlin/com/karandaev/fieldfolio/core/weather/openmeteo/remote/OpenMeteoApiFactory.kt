package com.karandaev.fieldfolio.core.weather.openmeteo.remote

import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.network.HttpClientConfig
import com.karandaev.fieldfolio.core.network.OkHttpClientFactory
import com.karandaev.fieldfolio.core.network.RetrofitFactory

/** Creates the default Open-Meteo Retrofit API used by the app. */
object OpenMeteoApiFactory {

  /** Creates an [OpenMeteoApi] with default network settings. */
  fun create(): OpenMeteoApi {
    val httpClient =
      OkHttpClientFactory.create(
        config =
          HttpClientConfig(
            // enableLogging = BuildConfig.DEBUG
          )
      )

    return RetrofitFactory.create(
      baseUrl = OpenMeteoEndpoints.BaseUrl,
      serviceClass = OpenMeteoApi::class.java,
      okHttpClient = httpClient,
      moshi = MoshiFactory.create(),
    )
  }
}
