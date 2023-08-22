package com.intellij.ml.llm.template.common.models.openai

import com.google.gson.annotations.SerializedName
import com.intellij.ml.llm.template.common.models.LLMBaseResponse
import com.intellij.ml.llm.template.common.models.LLMResponseChoice

data class OpenAIResponse(
    @SerializedName("object")
    val type: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("choices")
    val choices: List<ResponseChoice>,

    @SerializedName("usage")
    val usage: ResponseUsage,
) : LLMBaseResponse {
    override fun getSuggestions(): List<LLMResponseChoice> = choices.map {
        LLMResponseChoice(it.text, it.finishReason)
    }
}

data class OpenAIChatResponse (
    @SerializedName("id")
    val id: String,

    @SerializedName("object")
    val type: String,

    @SerializedName("created")
    val created: Long,

    @SerializedName("choices")
    val choices: List<ResponseChatChoice>,

    @SerializedName("usage")
    val usage: ResponseUsage,
) : LLMBaseResponse {
    override fun getSuggestions(): List<LLMResponseChoice> = choices.map {
        LLMResponseChoice(it.message.content, it.finishReason)
    }
}

data class ResponseChatChoice(
    @SerializedName("index")
    val index: Long,

    @SerializedName("message")
    val message: ResponseMessage,

    @SerializedName("finish_reason")
    val finishReason: String
)

data class ResponseChoice(
    @SerializedName("message")
    val text: String,

    @SerializedName("index")
    val index: Long,

    @SerializedName("logprobs")
    val logprobs: ResponseLogprobs,

    @SerializedName("finish_reason")
    val finishReason: String,
)

data class ResponseLogprobs(
    @SerializedName("tokens")
    val tokens: List<String>,

    @SerializedName("token_logprobs")
    val tokenLogprobs: List<Double>
)

data class ResponseUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Long,

    @SerializedName("completion_tokens")
    val completionTokens: Long,

    @SerializedName("total_tokens")
    val totalTokens: Long,
)

data class ResponseMessage (
    @SerializedName("role")
    val role: String,

    @SerializedName("content")
    val content: String
)