package com.intellij.ml.llm.template.models.openai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.ml.llm.template.models.LLMBaseRequest
import com.intellij.util.io.HttpRequests
import java.net.HttpURLConnection

open class OpenAIBaseRequest<Body>(path: String, body: Body) : LLMBaseRequest<Body>(body) {
    private val url = "https://api.openai.com/v1/$path"

    override fun sendSync(): OpenAIResponse? {
        val apiKey = CredentialsHolder.getInstance().getOpenAiApiKey()?.ifEmpty { null }
            ?: throw AuthorizationException("OpenAI API Key is not provided")

        return HttpRequests.post(url, "application/json")
            .tuner {
                it.setRequestProperty("Authorization", "Bearer $apiKey")
                CredentialsHolder.getInstance().getOpenAiOrganization()?.let { organization ->
                    it.setRequestProperty("OpenAI-Organization", organization)
                }
            }
            .connect { request ->
                request.write(GsonBuilder().create().toJson(body))

                val responseCode = (request.connection as HttpURLConnection).responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = request.readString()
                    Gson().fromJson(response, OpenAIResponse::class.java)
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

