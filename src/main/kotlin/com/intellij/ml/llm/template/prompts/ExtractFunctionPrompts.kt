package com.intellij.ml.llm.template.prompts

import com.intellij.ml.llm.template.models.openai.OpenAiChatMessage

fun fewShotExtractSuggestion(methodCode: String) = mutableListOf(
    OpenAiChatMessage(
        "system",
        """
                You are a skilled software developer. You have immense knowledge on software refactoring. 
                You communicate with a remote server that sends you code of functions (one function in a message) that it wants to simplify by applying extract method refactoring. 
                In return, you send a JSON object with suggestions of helpful extract method refactorings. 
                Each suggestion consists of the start line, end line, and name for the extracted function.
                The JSON should have the following format: [{"function_name": <new function name>, "line_start": <line start>, "line_end": <line end>}, ..., ].
                """.trimIndent()
    ),
    OpenAiChatMessage(
        "user",
        """
                1. fun floydWarshall(graph: Array<IntArray>): Array<IntArray> {
                2.    val n = graph.size
                3.  val dist = Array(n) { i -> graph[i].clone() }
                4.
                5.    for (k in 0 until n) {
                6.     for (i in 0 until n) {
                7.           for (j in 0 until n) {
                8.               if (dist[i][k] != Int.MAX_VALUE && dist[k][j] != Int.MAX_VALUE
                9.                && dist[i][k] + dist[k][j] < dist[i][j]
                10.             ) {
                11.               dist[i][j] = dist[i][k] + dist[k][j]
                12.            }
                13.       }
                14.    }
                15. }
                16.
                17.  return dist
                18.  }
                """
    ),
    OpenAiChatMessage(
        "assistant",
        """
                [
                {"function_name":  "floydWarshallUpdate", "line_start":  5, "line_end": 15}
                ]
                """
    ),
    OpenAiChatMessage(
        "user",
        methodCode
    )
)
