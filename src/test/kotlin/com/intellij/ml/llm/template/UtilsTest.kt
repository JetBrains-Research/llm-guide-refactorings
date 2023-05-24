package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.utils.identifyExtractFunctionSuggestions
import com.intellij.ml.llm.template.utils.replaceGithubUrlLineRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class UtilsTest : BasePlatformTestCase() {
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
}