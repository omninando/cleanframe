package tv.cleanframe

import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import org.json.JSONObject

data class Weather(
    val celsius: Int,
    val code: Int,
)

class WeatherClient {
    private var latitude: Double? = null
    private var longitude: Double? = null

    fun current(): Weather {
        val latitude = this.latitude
        val longitude = this.longitude
        val (lat, lon) = if (latitude != null && longitude != null) {
            latitude to longitude
        } else {
            locate().also {
                this.latitude = it.first
                this.longitude = it.second
            }
        }
        val body = get(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&temperature_unit=celsius",
        )
        val current = JSONObject(body).getJSONObject("current")
        return Weather(
            celsius = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
        )
    }

    private fun locate(): Pair<Double, Double> {
        val geo = JSONObject(get("https://get.geojs.io/v1/ip/geo.json"))
        return geo.getString("latitude").toDouble() to geo.getString("longitude").toDouble()
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CleanFrame")
        }
        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error("Weather request failed")
            return body
        } finally {
            connection.disconnect()
        }
    }
}
