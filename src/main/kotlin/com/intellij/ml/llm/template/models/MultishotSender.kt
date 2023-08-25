package com.intellij.ml.llm.template.models

import com.google.gson.annotations.SerializedName
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.openapi.project.Project
import java.util.concurrent.TimeUnit

data class LlmMultishotResponseData(
    @SerializedName("shotNo")
    val shotNo: Int,

    @SerializedName("processingTime")
    val processingTime: Long,

    @SerializedName("llmResponse")
    val llmResponse: LLMBaseResponse?
)

class MultishotSender(val llmRequestProvider: LLMRequestProvider, val project: Project) {
    fun sendRequest(data: String, maxShots: Int) : List<LlmMultishotResponseData> {
        val result = mutableListOf<LlmMultishotResponseData>()

        // get prompt
        val messageList = fewShotExtractSuggestion(data)

        for (shotNo in 1..maxShots) {
            val startTime = System.nanoTime()
            // send request
            val llmResponse = sendChatRequest(
                project, messageList, llmRequestProvider.chatModel, llmRequestProvider
            )

            val processingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            result.add(LlmMultishotResponseData(shotNo, processingTime, llmResponse))
        }

        return result.sortedBy { it.shotNo }
    }
}