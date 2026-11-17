package com.karandaev.fieldfolio.core.inspectionapi.remote

import com.karandaev.fieldfolio.core.json.MoshiFactory
import com.karandaev.fieldfolio.core.network.HttpClientConfig
import com.karandaev.fieldfolio.core.network.OkHttpClientFactory
import com.karandaev.fieldfolio.core.network.RetrofitFactory

/** Creates geo inspection Retrofit API instances. */
object InspectionApiFactory {

  /**
   * Creates a [InspectionApi] for the provided backend URL.
   *
   * @param baseUrl Base URL of the geo inspection backend. For example:
   *   `http://192.168.0.10:8443/`. Retrofit requires the URL to end with `/`.
   * @param config HTTP client configuration.
   */
  fun create(baseUrl: String, config: HttpClientConfig = HttpClientConfig()): InspectionApi {
    val httpClient = OkHttpClientFactory.create(config = config)

    return RetrofitFactory.create(
      baseUrl = baseUrl.normalizeInspectionBaseUrl(),
      serviceClass = InspectionApi::class.java,
      okHttpClient = httpClient,
      moshi = MoshiFactory.create(),
    )
  }
}
