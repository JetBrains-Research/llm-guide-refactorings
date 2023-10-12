package com.intellij.ml.llm.template.models

import com.google.gson.Gson
import com.intellij.ml.llm.template.models.openai.OpenAIChatResponse

class MockResponse(private val response: String) : LLMBaseResponse {
    override fun getSuggestions(): List<LLMResponseChoice> = listOf(LLMResponseChoice(response, ""))
}

class MockEditRequests(input: String) : LLMBaseRequest<String>(input) {
    override fun sendSync(): LLMBaseResponse {
        return MockResponse(mutate(body))
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
}

class MockCompletionRequests : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        return MockResponse("a = 15")
    }
}

class MockChatGPTRequest : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        return MockResponse("hello world from mock chat gpt")
    }
}

class MockExtractFunctionRequest(private val replyWith: String = "") : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        if (replyWith.isNotEmpty()) {
            return Gson().fromJson(replyWith, OpenAIChatResponse::class.java)
        }
        return Gson().fromJson(mockExtractFunctionJson(), OpenAIChatResponse::class.java)
    }

    private fun mockExtractFunctionJson(): String {
        return """
            {
              "id": "chatcmpl-88rJt9REKEwwLXs67TZyzZGqpBnGo",
              "object": "chat.completion",
              "created": 1697122277,
              "model": "gpt-3.5-turbo-0613",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "[\n{\"function_name\":  \"writeInterfaces\", \"line_start\":  69, \"line_end\": 75},\n{\"function_name\":  \"writeFields\", \"line_start\":  78, \"line_end\": 82},\n{\"function_name\":  \"writeMethods\", \"line_start\":  86, \"line_end\": 90},\n{\"function_name\":  \"writeAnnotationTargets\", \"line_start\":  94, \"line_end\": 98},\n{\"function_name\":  \"writeRetentionPolicy\", \"line_start\":  100, \"line_end\": 105}\n]"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 1116,
                "completion_tokens": 124,
                "total_tokens": 1240
              }
            }""".trimIndent()
    }
}
