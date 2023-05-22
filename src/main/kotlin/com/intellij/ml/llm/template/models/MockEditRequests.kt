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

class MockExtractFunctionRequest : LLMBaseRequest<String>("") {
    override fun sendSync(): LLMBaseResponse {
        return Gson().fromJson(mockExtractFunctionJson3(), OpenAIChatResponse::class.java)
    }
    private fun mockExtractFunctionJson3() : String {
        return """
            {"id":"chatcmpl-7Ga9htDtNUgyoywb3lC49nsV5l1Cw","object":"chat.completion","created":1684186585,"choices":[{"index":0,"message":{"role":"assistant","content":"                [\n                {\"function_name\":  \"createPartitionMetadata\", \"line_start\": 2317, \"line_end\": 2331},\n                {\"function_name\":  \"createRecordsToDelete\", \"line_start\": 2338, \"line_end\": 2343},\n                {\"function_name\":  \"handlePartitionResult\", \"line_start\": 2347, \"line_end\": 2351},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2354, \"line_end\": 2360},\n                {\"function_name\":  \"handleMetadataFailure\", \"line_start\": 2363, \"line_end\": 2369},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2371, \"line_end\": 2377},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2380, \"line_end\": 2386}\n                ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2299,"completion_tokens":193,"total_tokens":2492}}
        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7Ga9htDtNUgyoywb3lC49nsV5l1Cw","object":"chat.completion","created":1684186585,"choices":[{"index":0,"message":{"role":"assistant","content":"                [\n                {\"function_name\":  \"createRecordsToDelete\", \"line_start\": 2338, \"line_end\": 2343},\n                {\"function_name\":  \"handlePartitionResult\", \"line_start\": 2347, \"line_end\": 2351},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2354, \"line_end\": 2360},\n                {\"function_name\":  \"handleMetadataFailure\", \"line_start\": 2363, \"line_end\": 2369},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2371, \"line_end\": 2377},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2380, \"line_end\": 2386}\n                ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2299,"completion_tokens":193,"total_tokens":2492}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7Ga9htDtNUgyoywb3lC49nsV5l1Cw","object":"chat.completion","created":1684186585,"choices":[{"index":0,"message":{"role":"assistant","content":"                [\n                {\"function_name\":  \"handlePartitionResult\", \"line_start\": 2347, \"line_end\": 2351},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2354, \"line_end\": 2360},\n                {\"function_name\":  \"handleMetadataFailure\", \"line_start\": 2363, \"line_end\": 2369},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2371, \"line_end\": 2377},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2380, \"line_end\": 2386}\n                ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2299,"completion_tokens":193,"total_tokens":2492}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7Ga9htDtNUgyoywb3lC49nsV5l1Cw","object":"chat.completion","created":1684186585,"choices":[{"index":0,"message":{"role":"assistant","content":"                [\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2354, \"line_end\": 2360},\n                {\"function_name\":  \"handleMetadataFailure\", \"line_start\": 2363, \"line_end\": 2369},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2371, \"line_end\": 2377},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2380, \"line_end\": 2386}\n                ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2299,"completion_tokens":193,"total_tokens":2492}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7Ga9htDtNUgyoywb3lC49nsV5l1Cw","object":"chat.completion","created":1684186585,"choices":[{"index":0,"message":{"role":"assistant","content":"                [\n                {\"function_name\":  \"handleMetadataFailure\", \"line_start\": 2363, \"line_end\": 2369},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2371, \"line_end\": 2377},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2380, \"line_end\": 2386}\n                ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2299,"completion_tokens":193,"total_tokens":2492}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7Ga9htDtNUgyoywb3lC49nsV5l1Cw","object":"chat.completion","created":1684186585,"choices":[{"index":0,"message":{"role":"assistant","content":"                [\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2371, \"line_end\": 2377},\n                {\"function_name\":  \"handlePartitionFailure\", \"line_start\": 2380, \"line_end\": 2386}\n                ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2299,"completion_tokens":193,"total_tokens":2492}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7GaADjtI0YwphRz83TOxMJeUGBGKK","object":"chat.completion","created":1684186617,"choices":[{"index":0,"message":{"role":"assistant","content":"               [\n               {\"function_name\": \"createFetcher\", \"line_start\": 2685, \"line_end\": 2692},\n               {\"function_name\": \"createOffsetFetcher\", \"line_start\": 2693, \"line_end\": 2701},\n               {\"function_name\": \"createTopicMetadataFetcher\", \"line_start\": 2702, \"line_end\": 2702}\n               ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1456,"completion_tokens":134,"total_tokens":1590}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7GaAXgRhQ4jl2z71NxBz39IgVslCX","object":"chat.completion","created":1684186637,"choices":[{"index":0,"message":{"role":"assistant","content":"[\n{\"function_name\": \"testAllMessageRoundTripsOffsetFetchFromVersionV8AndAbove\", \"line_start\": 897, \"line_end\": 901}\n]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1787,"completion_tokens":196,"total_tokens":1983}}
//        """.trimIndent()
//        return """
//            {"id":"chatcmpl-7GaAvjLWOiKxAxTiHqrA7pDD65rWK","object":"chat.completion","created":1684186661,"choices":[{"index":0,"message":{"role":"assistant","content":"                 [\n{\"function_name\": \"handleScramDeletions\", \"line_start\": 3866, \"line_end\": 3876},\n                 {\"function_name\": \"handleScramUpsertions\", \"line_start\": 3881, \"line_end\": 3912},\n                 ]"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2157,"completion_tokens":88,"total_tokens":2245}}
//        """.trimIndent()
    }

    private fun mockExtractFunctionJson2() : String {
        return """
            {"id":"chatcmpl-7F6QK06TJAXmvATENvum9ksqRMYfo","object":"chat.completion","created":1683833968,"model":"gpt-3.5-turbo-0301","usage":{"prompt_tokens":1060,"completion_tokens":51,"total_tokens":1111},"choices":[{"message":{"role":"assistant","content":"```\n[\n{\"function_name\": \"getListFromCurrentValue\", \"line_start\": 229, \"line_end\": 242},\n{\"function_name\": \"someOtherFunctionName\", \"line_start\": 235, \"line_end\": 250}\n]\n```"},"finish_reason":"stop","index":0}]}
        """.trimIndent()
    }

    private fun mockExtractFunctionJson() : String {
        return """
            {
              "id": "chatcmpl-71ee3QSnvOnoRxpOyh2NvBxTFvZ40",
              "object": "chat.completion",
              "created": 1680628923,
              "model": "gpt-3.5-turbo-0301",
              "usage": {
                "prompt_tokens": 252,
                "completion_tokens": 207,
                "total_tokens": 459
              },
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "Here's my suggested splitting for the given Java function:
                       ```json
                       {
                          "suggestions": [
                            {
                              "new_function_name": "convertToUnixMillis",
                              "line_start": 8,
                              "line_end": 14
                            }
                          ]
                       }
                       ```
                       The reasons for splitting the function are:\n- Improve code re-usability and maintainability by 
                       splitting the schema validation logic into a separate function with a meaningful name. 
                       
                       Separating the conversion of a date object into Unix milliseconds to a new function makes the 
                       code more readable and easy to understand.
                       
                       Breaking out the validation of time fields set to non-zero values into a separate function 
                       promotes single responsibility and reduces the complexity of the original function."
                  },
                  "finish_reason": "stop",
                  "index": 0
                }
              ]
            }
        """.trimIndent()
    }
}
