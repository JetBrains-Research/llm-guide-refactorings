package com.intellij.ml.llm.template.models.openai

import com.google.gson.annotations.SerializedName
import com.intellij.ml.llm.template.models.LLMBaseResponse
import com.intellij.ml.llm.template.models.LLMResponseChoice

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

data class ResponseChoice(
    @SerializedName("text")
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