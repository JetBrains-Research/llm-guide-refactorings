package com.intellij.ml.llm.template.models

import com.google.gson.annotations.SerializedName
import com.intellij.ml.llm.template.extractfunction.EFSettingType
import com.intellij.ml.llm.template.extractfunction.EFSettings
import com.intellij.ml.llm.template.models.openai.OpenAiChatMessage
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.ml.llm.template.prompts.multishotExtractFunctionPrompt
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.Callable
import java.util.concurrent.ForkJoinPool
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
    fun sendRequest(
        data: String,
        existingShots: List<Int>,
        maxShots: Int,
        temperature: Double? = null
    ): List<LlmMultishotResponseData> {
        val result = mutableListOf<LlmMultishotResponseData>()

        // get prompt
        val messageList = if (EFSettings.instance.hasSetting(EFSettingType.MULTISHOT_LEARNING)) multishotExtractFunctionPrompt(data) else fewShotExtractSuggestion(data)
        val missingShots = getMissingShots(existingShots, maxShots)

        for (shotNo in missingShots) {
            val startTime = System.nanoTime()
            // send request
            val llmResponse = sendChatRequest(
                project = project,
                messages = messageList,
                model = llmRequestProvider.chatModel,
                temperature = temperature
            )

            val processingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
            result.add(LlmMultishotResponseData(shotNo, processingTime, llmResponse))
        }

        return result.sortedBy { it.shotNo }
    }

    private fun doRequest(shotNo: Int, project: Project, messageList:  MutableList<OpenAiChatMessage>, model: String, temperature: Double?): LlmMultishotResponseData {
        val startTime = System.nanoTime()
        val llmResponse = sendChatRequest(project=project, messages=messageList, model=model, temperature=temperature)
        val processingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
        return LlmMultishotResponseData(shotNo, processingTime, llmResponse)
    }
    fun sendRequestInPool(
            data: String,
            existingShots: List<Int>,
            maxShots: Int,
            temperature: Double? = null
    ): List<LlmMultishotResponseData> {
        val result = mutableListOf<LlmMultishotResponseData>()

        // get prompt
        val messageList = if (EFSettings.instance.hasSetting(EFSettingType.MULTISHOT_LEARNING)) multishotExtractFunctionPrompt(data) else fewShotExtractSuggestion(data)
        val missingShots = getMissingShots(existingShots, maxShots)

        val requests = mutableListOf<Int>()
        for (shotNo in missingShots) {
            requests.add(shotNo)
        }
        val ioPool = ForkJoinPool(5)
        return ioPool.submit(Callable {
            requests.parallelStream().map { doRequest(shotNo = it, project = project, messageList = messageList, model = llmRequestProvider.chatModel, temperature = temperature) }.toList()
        }).get().sortedBy { it.shotNo }
    }

    private fun getMissingShots(existingShots: List<Int>, maxShots: Int): List<Int> {
        val shots = (1..maxShots).toList()
        return shots.subtract(existingShots).toList()
    }
}