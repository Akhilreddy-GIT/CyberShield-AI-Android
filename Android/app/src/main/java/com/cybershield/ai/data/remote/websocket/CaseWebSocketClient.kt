package com.cybershield.ai.data.remote.websocket

import com.cybershield.ai.BuildConfig
import com.cybershield.ai.data.remote.dto.CaseWsUpdateDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to backend `/ws/case/{case_id}` and emits live status/risk updates.
 */
@Singleton
class CaseWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    fun observeCase(caseId: String): Flow<CaseWsUpdateDto> = callbackFlow {
        val adapter = moshi.adapter(CaseWsUpdateDto::class.java)
        val url = BuildConfig.WS_BASE_URL.trimEnd('/') + "/ws/case/$caseId"
        val request = Request.Builder().url(url).build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val update = runCatching { adapter.fromJson(text) }.getOrNull()
                if (update != null) {
                    trySend(update)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                close()
            }
        }

        val socket = okHttpClient.newWebSocket(request, listener)
        awaitClose {
            socket.close(1000, "client closed")
        }
    }
}
