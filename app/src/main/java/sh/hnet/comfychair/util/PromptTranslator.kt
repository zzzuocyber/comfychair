package sh.hnet.comfychair.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.hnet.comfychair.storage.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Translates a prompt between Chinese and English through any OpenAI-compatible
 * chat completions endpoint (DeepSeek by default, configurable in settings).
 */
object PromptTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Result of a translation attempt: the text, or a human-readable error. */
    sealed class Result {
        data class Success(val text: String) : Result()
        data class Error(val message: String) : Result()
    }

    /** True when the text contains a CJK ideograph — used to pick the translation direction. */
    private fun containsChinese(text: String): Boolean =
        text.any { it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF }

    /**
     * Translate [text] between Chinese and English.
     * Direction is inferred: Chinese present -> to English, otherwise -> to Chinese.
     */
    suspend fun translate(context: Context, text: String): Result = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.Error("")

        val baseUrl = AppSettings.getTranslateBaseUrl(context).trim().trimEnd('/')
        val apiKey = AppSettings.getTranslateApiKey(context).trim()
        val model = AppSettings.getTranslateModel(context).trim().ifEmpty { AppSettings.DEFAULT_TRANSLATE_MODEL }

        if (apiKey.isEmpty()) {
            return@withContext Result.Error("请先在 设置 → 生成 → 翻译设置 里填写 API Key")
        }
        val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val direction = if (containsChinese(text)) {
            "Translate the following text into natural, fluent English. It is an AI image-generation prompt: keep style, quality and technical keywords accurate, preserve punctuation and weight syntax such as parentheses. Output ONLY the translation, with no explanation or quotes."
        } else {
            "Translate the following text into natural, fluent Chinese. It is an AI image-generation prompt: keep style, quality and technical keywords accurate. Output ONLY the translation, with no explanation or quotes."
        }

        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.3)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", direction))
                put(JSONObject().put("role", "user").put("content", text))
            })
        }.toString()

        val request = try {
            Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        } catch (e: Exception) {
            return@withContext Result.Error("API 地址无效：${e.message ?: e.javaClass.simpleName}")
        }

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        JSONObject(raw).optJSONObject("error")?.optString("message")
                    }.getOrNull().takeUnless { it.isNullOrEmpty() }
                    return@withContext Result.Error("API 返回 ${response.code}: ${detail ?: raw.take(160)}")
                }
                val translated = JSONObject(raw)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                if (translated.isNullOrEmpty()) {
                    return@withContext Result.Error("翻译结果为空，请检查模型名是否正确")
                }
                // Some models wrap the answer in quotes despite instructions; strip a matching pair.
                val cleaned = if (translated.length >= 2 &&
                    ((translated.first() == '"' && translated.last() == '"') ||
                     (translated.first() == '\'' && translated.last() == '\''))
                ) translated.substring(1, translated.length - 1).trim() else translated
                return@withContext Result.Success(cleaned)
            }
        } catch (e: Exception) {
            return@withContext Result.Error("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }
}
