package com.clineagentmobile.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class OpenRouterModel(
    val id: String,
    val name: String
)

object OpenRouterCatalog {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchFreeToolModels(): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("OpenRouter catalog unavailable (HTTP ${response.code})")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Risposta vuota dal catalogo OpenRouter")
            parseFreeToolModels(body)
        }
    }

    suspend fun verifyApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/key")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Invalid OpenRouter key (HTTP ${response.code})")
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("OpenRouter ha restituito una risposta vuota")
            val data = JsonParser.parseString(body).asJsonObject.get("data")
            if (data == null || !data.isJsonObject) {
                throw IllegalStateException("Invalid OpenRouter verification response")
            }
        }
    }

    internal fun parseFreeToolModels(json: String): List<OpenRouterModel> {
        val data = JsonParser.parseString(json).asJsonObject.getAsJsonArray("data")
            ?: throw IllegalStateException("OpenRouter catalog contains no models")
        return data.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val model = element.asJsonObject
            val id = model.getString("id") ?: return@mapNotNull null
            val pricing = model.getAsJsonObject("pricing") ?: return@mapNotNull null
            if (!id.endsWith(":free") ||
                !pricing.getString("prompt").isFreePrice() ||
                !pricing.getString("completion").isFreePrice()
            ) {
                return@mapNotNull null
            }
            val parameters = model.getAsJsonArray("supported_parameters")
                ?: return@mapNotNull null
            if (parameters.none { it.isJsonPrimitive && it.asString == "tools" }) {
                return@mapNotNull null
            }
            OpenRouterModel(id, model.getString("name") ?: id)
        }.sortedWith(compareBy<OpenRouterModel> { it.name.lowercase() }.thenBy { it.id })
    }

    private fun JsonObject.getString(key: String): String? {
        val value = get(key) ?: return null
        return value.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString
    }

    private fun String?.isFreePrice(): Boolean =
        this?.toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ZERO) == 0
}
