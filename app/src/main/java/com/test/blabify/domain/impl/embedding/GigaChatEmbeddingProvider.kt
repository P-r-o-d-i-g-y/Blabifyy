package com.test.blabify.domain.impl.embedding

import android.util.Log
import com.test.blabify.BuildConfig
import com.test.blabify.domain.api.EmbeddingProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class GigaChatEmbeddingProvider(
    private val authKey: String = BuildConfig.GIGACHAT_AUTH_KEY,
    private val scope: String = "GIGACHAT_API_PERS",
    private val model: String = "Embeddings-2"
) : EmbeddingProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var accessToken: String? = null
    private var expiresAt: Long = 0L

    override suspend fun embed(text: String): List<Double> {
        return embedAll(listOf(text)).firstOrNull().orEmpty()
    }

    override suspend fun embedAll(texts: List<String>): List<List<Double>> {
        val normalizedTexts = texts
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (normalizedTexts.isEmpty()) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val token = getAccessToken()
            requestEmbeddings(token, normalizedTexts)
        }
    }

    private fun getAccessToken(): String {
        val cachedToken = accessToken

        if (!cachedToken.isNullOrBlank() && System.currentTimeMillis() < expiresAt - TOKEN_REFRESH_MARGIN_MS) {
            return cachedToken
        }

        val body = FormBody.Builder()
            .add("scope", scope)
            .build()

        val request = Request.Builder()
            .url(OAUTH_URL)
            .post(body)
            .addHeader("Authorization", "Basic $authKey")
            .addHeader("RqUID", UUID.randomUUID().toString())
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "GigaChat auth failed: code=${response.code}, body=$responseBody"
                )
            }

            val json = JSONObject(responseBody)

            accessToken = json.getString("access_token")
            expiresAt = json.optLong("expires_at", System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS)

            return accessToken.orEmpty()
        }
    }

    private fun requestEmbeddings(
        token: String,
        texts: List<String>
    ): List<List<Double>> {
        val input = JSONArray().apply {
            texts.forEach { text ->
                put(text)
            }
        }

        val jsonBody = JSONObject()
            .put("model", model)
            .put("input", input)
            .toString()

        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(EMBEDDINGS_URL)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "GigaChat embeddings failed: code=${response.code}, body=$responseBody"
                )
            }

            return parseEmbeddings(responseBody)
        }
    }

    private fun parseEmbeddings(responseBody: String): List<List<Double>> {
        val json = JSONObject(responseBody)
        val data = json.getJSONArray("data")

        return List(data.length()) { dataIndex ->
            val item = data.getJSONObject(dataIndex)
            val embedding = item.getJSONArray("embedding")

            List(embedding.length()) { valueIndex ->
                embedding.getDouble(valueIndex)
            }
        }
    }

    private companion object {
        private const val OAUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
        private const val EMBEDDINGS_URL = "https://gigachat.devices.sberbank.ru/api/v1/embeddings"

        private const val TOKEN_REFRESH_MARGIN_MS = 60_000L
        private const val DEFAULT_TOKEN_TTL_MS = 30 * 60 * 1000L

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}