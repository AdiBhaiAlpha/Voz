package com.example.online

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens are encrypted with a non-exportable Android Keystore AES-GCM key. Backup is disabled. */
class OnlineSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("voz_online_secure", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("voz_online_session", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply { init(KeyGenParameterSpec.Builder("voz_online_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }
    @Synchronized fun load(): JSONObject? = try {
        prefs.getString("session", null)?.let {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        }
    } catch (_: Exception) { clear(); null }
    @Synchronized fun save(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs.edit().putString("session", Base64.encodeToString(cipher.iv + cipher.doFinal(value.toString().toByteArray()), Base64.NO_WRAP)).commit()
    }
    @Synchronized fun clear() { prefs.edit().remove("session").commit() }
    val installationId: String get() = prefs.getString("installation", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("installation", it).commit()
    }
}

class OnlineApi(context: Context) {
    val store = OnlineSessionStore(context.applicationContext)
    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private val refreshLock = Mutex()
    private val base = BuildConfig.VOZ_SERVICE_URL.trimEnd('/')
    suspend fun request(method: String, path: String, body: JSONObject? = null, authorized: Boolean = true): JSONObject {
        check(base.startsWith("https://")) { "Online service is not configured" }
        val before = store.load()?.optString("accessToken")
        var response = execute(method, path, body, if (authorized) before else null)
        if (response.first == 401 && authorized) {
            refreshLock.withLock {
                val latest = store.load()
                if (latest?.optString("accessToken") == before) {
                    val refreshed = execute("POST", "/v1/auth/refresh", JSONObject().put("refreshToken", latest?.optString("refreshToken")), null)
                    if (refreshed.first !in 200..299) { store.clear(); error("Sign in again") }
                    store.save(refreshed.second)
                }
            }
            response = execute(method, path, body, store.load()?.optString("accessToken"))
        }
        if (response.first !in 200..299) error(response.second.optString("error", "Service unavailable"))
        return response.second
    }
    private suspend fun execute(method: String, path: String, body: JSONObject?, token: String?): Pair<Int, JSONObject> = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(base + path)
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        val requestBody = if (method in listOf("POST", "PUT")) (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()) else null
        val call = client.newCall(builder.method(method, requestBody).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try { response.use {
                    val result = it.code to JSONObject(it.body?.string().orEmpty().ifBlank { "{}" })
                    if (continuation.isActive) continuation.resume(result)
                } } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
    suspend fun command(id: String, action: String, payload: JSONObject = JSONObject(), eventId: String = UUID.randomUUID().toString()): JSONObject =
        request("POST", "/v1/calls/$id/events", JSONObject().put("eventId", eventId).put("action", action).put("payload", payload))
}
