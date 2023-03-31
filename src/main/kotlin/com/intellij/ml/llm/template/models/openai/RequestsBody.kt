package com.intellij.ml.llm.template.models.openai

import com.google.gson.annotations.SerializedName

@Suppress("unused")
/**
 * Documentation: https://beta.openai.com/docs/api-reference/edits
 */
class OpenAiEditRequestBody(
    @SerializedName("model")
    val model: String,

    @SerializedName("input")
    val input: String,

    @SerializedName("instruction")
    val instruction: String,

    @SerializedName("n")
    val numberOfSuggestions: Int,

    @SerializedName("temperature")
    val temperature: Double,

    @SerializedName("top_p")
    val topP: Double,
)

@Suppress("unused")
/**
 * Documentation: https://beta.openai.com/docs/api-reference/completions
 */
class OpenAiCompletionRequestBody(
    @SerializedName("model")
    val model: String,

    @SerializedName("prompt")
    val prompt: String,

    @SerializedName("max_tokens")
    val maxTokens: Int,

    @SerializedName("temperature")
    val temperature: Double,

    @SerializedName("top_p")
    val topP: Double,

    @SerializedName("n")
    val numberOfSuggestions: Int,

    @SerializedName("suffix")
    val suffix: String? = null,

    @SerializedName("stream")
    val stream: Boolean? = null,

    @SerializedName("logprobs")
    val logprobs: Int? = null,

    @SerializedName("echo")
    val echo: Boolean = false,

    @SerializedName("stop")
    val stop: String? = null,

    @SerializedName("presence_penalty")
    val presencePenalty: Double? = null,

    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double? = null,

    @SerializedName("best_of")
    val bestOf: Int? = null,

    @SerializedName("logit_bias")
    val logitBias: Map<String, Int>? = null,
)

data class OpenAiChatMessage (
    @SerializedName("role")
    val role: String,

    @SerializedName("content")
    val content: String,
)

@Suppress("unused")
/**
 * Documentation: https://platform.openai.com/docs/api-reference/chat
 */
class OpenAiChatRequestBody(
    @SerializedName("model")
    val model: String,

    @SerializedName("messages")
    val messages: List<OpenAiChatMessage>,

    @SerializedName("temperature")
    val temperature: Double? = null,

    @SerializedName("top_p")
    val topP: Double? = null,

    @SerializedName("n")
    val numberOfSuggestions: Int? = null,

    @SerializedName("stream")
    var stream: Boolean? = null,

    @SerializedName("stop")
    val stop: String? = null,

    @SerializedName("max_tokens")
    val maxTokens: Int? = null,

    @SerializedName("presence_penalty")
    val presencePenalty: Double? = null,

    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double? = null,

    @SerializedName("logit_bias")
    val logitBias: Map<String, Int>? = null,

    @SerializedName("user")
    val user: String? = null,
)