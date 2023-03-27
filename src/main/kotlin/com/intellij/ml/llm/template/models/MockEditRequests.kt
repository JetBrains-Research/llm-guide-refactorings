package com.intellij.ml.llm.template.models

class MockResponse(private val response: String) : LLMBaseResponse {
    override fun getSuggestions(): List<LLMResponseChoice> = listOf(LLMResponseChoice(response, ""))
}

class MockEditRequests(input: String) : LLMBaseRequest<String>(input) {
    override fun sendSync(): LLMBaseResponse {
        return MockResponse(mockJson())
    }

    /**
     * Mutate input string to make the change visible
     */
    private fun mutate(input: String): String {
        if (input.isEmpty()) {
            return ""
        }

        val array = input.toCharArray()
        array[0] = array[0].inc()
        return array.joinToString(separator = "")
    }

    private fun mockJson() : String {
        return """{"functionName":"getConnector","lineStart":92,"lineEnd": 92}"""
    }
}

class MockCompletionRequests : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        return MockResponse("a = 15")
    }
}