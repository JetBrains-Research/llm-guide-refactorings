package com.intellij.ml.llm.template.utils

import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EFSuggestionList

fun addLineNumbersToCodeSnippet(codeSnippet: String, startIndex: Int): String {
    val lines = codeSnippet.lines()
    val numberedLines = lines.mapIndexed { index, line -> "${startIndex + index}. $line" }
    return numberedLines.joinToString("\n")
}


fun replaceGithubUrlLineRange(githubUrl: String, lineStart: Int, lineEnd: Int): String {
    val newLineRange = String.format("L$lineStart-L$lineEnd")
    return String.format("${githubUrl.substringBefore("#")}#$newLineRange")
}

fun identifyExtractFunctionSuggestions(input: String): EFSuggestionList {
    val regex = """\{"function_name":\s*"([^"]+)",\s*"line_start":\s*(\d+),\s*"line_end":\s*(\d+)\}""".toRegex()
    val matches = regex.findAll(input)
    val efSuggestions = mutableListOf<EFSuggestion>()
    for (match in matches) {
        efSuggestions.add(
            EFSuggestion(
            functionName = match.groupValues[1],
            lineStart = match.groupValues[2].toInt(),
            lineEnd = match.groupValues[3].toInt(),
        )
        )
    }

    return EFSuggestionList(efSuggestions)
}
