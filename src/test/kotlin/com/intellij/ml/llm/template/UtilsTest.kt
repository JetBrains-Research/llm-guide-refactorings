package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.models.ExtractFunctionLLMRequestProvider
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.sendChatRequest
import com.intellij.ml.llm.template.utils.*
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase

class UtilsTest : LightPlatformCodeInsightTestCase() {
    private var projectPath = "src/test"
    override fun getTestDataPath(): String {
        return projectPath
    }

    fun `test identify extract function suggestions from chatgpt reply`() {
        val input = """
            I would suggest the following extract method refactorings:

            1. Extract method for creating `GroupRebalanceConfig` object from lines 2650-2656.
            2. Extract method for creating `ConsumerCoordinator` object from lines 2649-2670.
            3. Extract method for creating `FetchConfig` object from lines 2674-2684.
            4. Extract method for creating `Fetcher` object from lines 2685-2692.
            5. Extract method for creating `OffsetFetcher` object from lines 2693-2701.
            6. Extract method for creating `TopicMetadataFetcher` object from lines 2702-2703.

            The JSON object for these extract method refactorings would be:

            ```
            [
                {"function_name": "createRebalanceConfig", "line_start": 2650, "line_end": 2656},
                {"function_name": "createConsumerCoordinator", "line_start": 2649, "line_end": 2670},
                {"function_name": "createFetchConfig", "line_start": 2674, "line_end": 2684},
                {"function_name": "createFetcher", "line_start": 2685, "line_end": 2692},
                {"function_name": "createOffsetFetcher", "line_start": 2693, "line_end": 2701},
                {"function_name": "createTopicMetadataFetcher", "line_start": 2702, "line_end": 2703}
            ]
            ```        
            """.trimIndent()
        val efSuggestionList = identifyExtractFunctionSuggestions(input)

        TestCase.assertEquals(6, efSuggestionList.suggestionList.size)
        TestCase.assertEquals("createRebalanceConfig", efSuggestionList.suggestionList.get(0).functionName)
        TestCase.assertEquals(
            2650 to 2656,
            efSuggestionList.suggestionList.get(0).lineStart to efSuggestionList.suggestionList.get(0).lineEnd
        )
        TestCase.assertEquals("createConsumerCoordinator", efSuggestionList.suggestionList.get(1).functionName)
        TestCase.assertEquals(
            2649 to 2670,
            efSuggestionList.suggestionList.get(1).lineStart to efSuggestionList.suggestionList.get(1).lineEnd
        )
        TestCase.assertEquals("createFetchConfig", efSuggestionList.suggestionList.get(2).functionName)
        TestCase.assertEquals(
            2674 to 2684,
            efSuggestionList.suggestionList.get(2).lineStart to efSuggestionList.suggestionList.get(2).lineEnd
        )
        TestCase.assertEquals("createFetcher", efSuggestionList.suggestionList.get(3).functionName)
        TestCase.assertEquals(
            2685 to 2692,
            efSuggestionList.suggestionList.get(3).lineStart to efSuggestionList.suggestionList.get(3).lineEnd
        )
        TestCase.assertEquals("createOffsetFetcher", efSuggestionList.suggestionList.get(4).functionName)
        TestCase.assertEquals(
            2693 to 2701,
            efSuggestionList.suggestionList.get(4).lineStart to efSuggestionList.suggestionList.get(4).lineEnd
        )
        TestCase.assertEquals("createTopicMetadataFetcher", efSuggestionList.suggestionList.get(5).functionName)
        TestCase.assertEquals(
            2702 to 2703,
            efSuggestionList.suggestionList.get(5).lineStart to efSuggestionList.suggestionList.get(5).lineEnd
        )
    }

    fun `test identify extract function suggestions from ChatGPT reply in mock mode`() {
        com.intellij.openapi.util.registry.Registry.get("llm.for.code.enable.mock.requests").setValue(true)
        val mockReply = """
            {"id":"chatcmpl-7ODaGfLVvacxtdsodruHUvYswiDwH","object":"chat.completion","created":1686006444,"model":"gpt-3.5-turbo-0301","usage":{"prompt_tokens":1830,"completion_tokens":70,"total_tokens":1900},"choices":[{"message":{"role":"assistant","content":"[\n{\"function_name\": \"advanceReadInputCharacter\", \"line_start\": 646, \"line_end\": 691},\n{\"function_name\": \"getNextTransition\", \"line_start\": 692, \"line_end\": 703},\n{\"function_name\": \"handleAction\", \"line_start\": 714, \"line_end\": 788}\n]"},"finish_reason":"stop","index":0}]}
        """.trimIndent()
        val efLLMRequestProvider: LLMRequestProvider =
            ExtractFunctionLLMRequestProvider("text-davinci-003", "text-davinci-edit-001", "gpt-3.5-turbo", mockReply)
        val llmResponse = sendChatRequest(
            project, emptyList(), efLLMRequestProvider.chatModel, efLLMRequestProvider
        )

        val llmSuggestions = llmResponse?.getSuggestions()
        val efSuggestions = identifyExtractFunctionSuggestions(llmSuggestions?.get(0)?.text!!).suggestionList

        TestCase.assertEquals(3, efSuggestions.size)
    }

    fun `test no identifiable suggestions in chatgpt reply`() {
        val input = """
            "The above function does not have any extract method opportunities."
        """.trimIndent()

        val efSuggestionList = identifyExtractFunctionSuggestions(input)

        TestCase.assertEquals(0, efSuggestionList.suggestionList.size)
    }

    fun `test replace github url line range`() {
        val url =
            "https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/requests/UpdateMetadataRequestTest.java#L81-L212"
        val expectedUrl =
            "https://github.com/apache/kafka/blob/trunk/clients/src/test/java/org/apache/kafka/common/requests/UpdateMetadataRequestTest.java#L90-L120"
        val actualUrl = replaceGithubUrlLineRange(url, 90, 120)
        TestCase.assertEquals(expectedUrl, actualUrl)
    }

    fun `test isCandidateExtractable generates correct notifications`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efSuggestion = EFSuggestion(
            functionName = "foo",
            lineStart = 114,
            lineEnd = 119
        )
        val efObserver = EFObserver()
        val candidates = EFCandidateFactory().buildCandidates(efSuggestion, editor, file).toTypedArray()
        TestCase.assertEquals(2, candidates.size)

        candidates.forEach { isCandidateExtractable(it, editor, file, listOf(efObserver)) }
        val notifications = efObserver.getNotifications()
        TestCase.assertEquals(2, notifications.size)
        val firstNotifPayload = notifications[0].payload as EFCandidateApplicationPayload
        val secondNotifPayload = notifications[1].payload as EFCandidateApplicationPayload
        TestCase.assertEquals(EFApplicationResult.OK, firstNotifPayload.result)
        TestCase.assertEquals(EFApplicationResult.FAIL, secondNotifPayload.result)
    }
}