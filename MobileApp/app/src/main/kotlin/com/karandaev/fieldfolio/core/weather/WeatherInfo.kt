package com.karandaev.fieldfolio.core.weather

/**
 * Domain model representing current weather information.
 *
 * This model is independent from network DTOs, specific weather providers, UI descriptions, icons,
 * and location display names.
 */
data class WeatherInfo(val temperature: Double, val weatherCode: Int, val zoneId: String? = null)
