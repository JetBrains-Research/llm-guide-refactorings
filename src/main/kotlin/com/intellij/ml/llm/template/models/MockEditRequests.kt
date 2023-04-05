package com.intellij.ml.llm.template.models

import com.google.gson.Gson
import com.intellij.ml.llm.template.models.openai.OpenAIChatResponse

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
        return """{"new_function_name":"getConnector","line_start":62,"line_end": 70}"""
    }
}

class MockCompletionRequests : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        return MockResponse("a = 15")
    }
}

class MockChatGPTRequest : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        return Gson().fromJson(mockJson(), OpenAIChatResponse::class.java)
    }
    private fun mockJson() : String {
        return """{"id":"chatcmpl-71ee3QSnvOnoRxpOyh2NvBxTFvZ40","object":"chat.completion","created":1680628923,"model":"gpt-3.5-turbo-0301","usage":{"prompt_tokens":252,"completion_tokens":207,"total_tokens":459},"choices":[{"message":{"role":"assistant","content":"Here's my suggested splitting for the given Java function:\n\n```json\n{\n  \"suggestions\": [\n    {\n      \"new_function_name\": \"convertToUnixMillis\",\n      \"line_start\": 8,\n      \"line_end\": 14\n    },\n]\n}\n```\n\nThe reasons for splitting the function are:\n- Improve code re-usability and maintainability by splitting the schema validation logic into a separate function with a meaningful name.\n- Separating the conversion of a date object into Unix milliseconds to a new function makes the code more readable and easy to understand.\n- Breaking out the validation of time fields set to non-zero values into a separate function promotes single responsibility and reduces the complexity of the original function."},"finish_reason":"stop","index":0}]}"""
    }
}
