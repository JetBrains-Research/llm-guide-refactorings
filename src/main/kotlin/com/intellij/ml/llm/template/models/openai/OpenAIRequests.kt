package com.intellij.ml.llm.template.models.openai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.ml.llm.template.models.LLMBaseRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import java.net.HttpURLConnection

open class OpenAIBaseRequest<Body>(path: String, body: Body) : LLMBaseRequest<Body>(body) {
    private val url = "https://api.openai.com/v1/$path"

    override fun sendSync(): OpenAIChatResponse? {
        val apiKey = CredentialsHolder.getInstance().getOpenAiApiKey()?.ifEmpty { null }
            ?: throw AuthorizationException("OpenAI API Key is not provided")
        val jsonBody = GsonBuilder().create().toJson(body)

        return HttpRequests.post(url, "application/json")
            .tuner {
                it.setRequestProperty("Authorization", "Bearer $apiKey")
                CredentialsHolder.getInstance().getOpenAiOrganization()?.let { organization ->
                    it.setRequestProperty("OpenAI-Organization", organization)
                }
            }
            .connect { request -> request.write(jsonBody)
                val responseCode = (request.connection as HttpURLConnection).responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = request.readString()
                    Logger.getInstance("#com.intellij.ml.llm").info("Raw response:\n${response}")
                    Gson().fromJson(response, OpenAIChatResponse::class.java)
                } else {
                    null
                }
            }
    }
}

class OpenAIEditRequest(body: OpenAiEditRequestBody) :
    OpenAIBaseRequest<OpenAiEditRequestBody>("edits", body)

class OpenAICompletionRequest(body: OpenAiCompletionRequestBody) :
    OpenAIBaseRequest<OpenAiCompletionRequestBody>("completions", body)

class OpenAIChatRequest(body: OpenAiChatRequestBody) :
    OpenAIBaseRequest<OpenAiChatRequestBody>("chat/completions", body)

