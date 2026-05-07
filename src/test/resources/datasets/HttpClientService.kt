package codebase.service

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpClientService(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 10000
) {

    suspend fun fetchJson(endpoint: String): String = withContext(Dispatchers.IO) {
        val url = URI.create("$baseUrl/$endpoint").toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "CodebaseHttpClient/1.0")
        connection.requestMethod = "GET"

        try {
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw HttpException(responseCode, "HTTP $responseCode: $errorBody")
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun postJson(endpoint: String, body: String): String = withContext(Dispatchers.IO) {
        val url = URI.create("$baseUrl/$endpoint").toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.requestMethod = "POST"

        try {
            connection.outputStream.use { os ->
                os.write(body.toByteArray())
            }
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw HttpException(responseCode, "HTTP $responseCode: $errorBody")
            }
        } finally {
            connection.disconnect()
        }
    }

    class HttpException(val statusCode: Int, message: String) : RuntimeException(message)
}
