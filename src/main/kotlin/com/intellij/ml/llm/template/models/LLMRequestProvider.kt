package com.intellij.ml.llm.template.models

import com.intellij.ml.llm.template.models.openai.*
import com.intellij.ml.llm.template.settings.LLMSettingsManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry


/**
 * Available options: https://beta.openai.com/docs/models/codex
 */
private const val CODEX_COMPLETION_MODEL = "code-davinci-002"
private const val CODEX_EDIT_MODEL = "code-davinci-edit-001"

private const val GPT_COMPLETION_MODEL = "text-davinci-003"
private const val GPT_EDIT_MODEL = "text-davinci-edit-001"

private const val CHAT_GPT_3_5_TURBO = "gpt-3.5-turbo"

private val logger = Logger.getInstance("#com.intellij.ml.llm.template.models")

val CodexRequestProvider = LLMRequestProvider(CODEX_COMPLETION_MODEL, CODEX_EDIT_MODEL, CHAT_GPT_3_5_TURBO)

val GPTRequestProvider = LLMRequestProvider(GPT_COMPLETION_MODEL, GPT_EDIT_MODEL, CHAT_GPT_3_5_TURBO)

class LLMRequestProvider(
    val completionModel: String,
    val editModel: String,
    val chatModel: String,
) {
    fun createEditRequest(
        input: String,
        instruction: String,
        temperature: Double,
        topP: Double,
        numberOfSuggestions: Int
    ): LLMBaseRequest<*> {
        if (Registry.`is`("llm.for.code.enable.mock.requests")) {
            logger.info("Emulating request to the API to test response presentation")
            return MockEditRequests(input)
        }

        val body = OpenAiEditRequestBody(
            model = editModel,
            input = input,
            instruction = instruction,
            numberOfSuggestions = numberOfSuggestions,
            temperature = temperature,
            topP = topP
        )

        logger.info(
            "Sending request to OpenAI API with temperature=$temperature, topP=$topP, suggestions=$numberOfSuggestions"
        )
        return OpenAIEditRequest(body)
    }

    fun createChatGPTRequest(
        body: OpenAiChatRequestBody,
    ): LLMBaseRequest<*> {
        if (Registry.`is`("llm.for.code.enable.mock.requests")) {
            logger.info("Emulating request to the API to test response presentation")
            return MockChatGPTRequest()
        }

        logger.info(
            "Sending request to OpenAI API with model=$chatModel and messages=${body.messages}"
        )

        return OpenAIChatRequest(body)
    }

    fun createCompletionRequest(
        input: String,
        suffix: String,
        maxTokens: Int? = null,
        temperature: Double? = null,
        presencePenalty: Double? = null,
        frequencyPenalty: Double? = null,
        topP: Double? = null,
        numberOfSuggestions: Int? = null,
        logProbs: Int? = null,
    ): LLMBaseRequest<*> {
        if (Registry.`is`("llm.for.code.enable.mock.requests")) {
            logger.info("Emulating request to the API to test response presentation")
            return MockCompletionRequests()
        }

        return createOpenAiCompletionRequest(
            input, suffix, maxTokens, numberOfSuggestions, temperature, logProbs, topP,
            presencePenalty, frequencyPenalty
        )
    }

    private fun createOpenAiCompletionRequest(
        input: String,
        suffix: String,
        maxTokens: Int?,
        numberOfSuggestions: Int?,
        temperature: Double?,
        logProbs: Int?,
        topP: Double?,
        presencePenalty: Double? = null,
        frequencyPenalty: Double? = null,
    ): OpenAICompletionRequest {
        val settings = LLMSettingsManager.getInstance()
        val body = OpenAiCompletionRequestBody(
            model = completionModel,
            prompt = input,
            suffix = suffix,
            maxTokens = maxTokens ?: settings.getMaxTokens(),
            numberOfSuggestions = numberOfSuggestions ?: settings.getNumberOfSamples(),
            temperature = temperature ?: settings.getTemperature(),
            topP = settings.getTopP(),
            logprobs = logProbs,
            presencePenalty = presencePenalty ?: settings.getPresencePenalty(),
            frequencyPenalty = frequencyPenalty ?: settings.getFrequencyPenalty()
        )

        logger.info(
            "Sending completion request to OpenAI API with " +
                    "maxTokens=$maxTokens, temperature=$temperature, topP=$topP, suggestions=$numberOfSuggestions"
        )
        return OpenAICompletionRequest(body)
    }
}