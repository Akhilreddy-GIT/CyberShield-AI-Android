package com.cybershield.ai.core

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

fun Throwable.toUserMessage(): String {
    return when (this) {
        is HttpException -> {
            val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
            val detail = body?.let {
                runCatching { JSONObject(it).optString("detail") }.getOrNull()?.takeIf { d -> d.isNotBlank() }
            }
            when {
                !detail.isNullOrBlank() -> detail
                !body.isNullOrBlank() -> body.take(240)
                code() == 401 -> "Invalid username or password"
                code() == 404 -> "Not found"
                code() == 409 -> "Username already taken"
                else -> "Server error (${code()})"
            }
        }
        is IOException -> "Cannot reach server. Is the backend running on port 8000?"
        else -> message ?: "Something went wrong"
    }
}
