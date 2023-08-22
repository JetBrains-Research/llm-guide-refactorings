package com.intellij.ml.llm.template.common.models

import com.intellij.ml.llm.template.common.models.openai.OpenAICompletionRequest
import com.intellij.ml.llm.template.common.models.openai.OpenAIEditRequest

data class LLMResponseChoice(val text: String, val finishReason: String?)

interface LLMBaseResponse {
    fun getSuggestions(): List<LLMResponseChoice>
}

abstract class LLMBaseRequest<Body>(val body: Body) {
    abstract fun sendSync(): LLMBaseResponse?
}

enum class LLMRequestType {
    OPENAI_EDIT, OPENAI_COMPLETION, MOCK;

    companion object {
        fun byRequest(request: LLMBaseRequest<*>): LLMRequestType {
            return when (request) {
                is OpenAIEditRequest -> OPENAI_EDIT
                is OpenAICompletionRequest -> OPENAI_COMPLETION
                else -> MOCK
            }
        }
    }
}