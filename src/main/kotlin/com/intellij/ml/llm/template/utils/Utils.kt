package com.intellij.ml.llm.template.utils

import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EFSuggestionList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.findAllOptionsToExtract
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.psi.KtFile


fun addLineNumbersToCodeSnippet(codeSnippet: String, startIndex: Int): String {
    val lines = codeSnippet.lines()
    val numberedLines = lines.mapIndexed { index, line -> "${startIndex + index}. $line" }
    return numberedLines.joinToString("\n")
}


fun replaceGithubUrlLineRange(githubUrl: String, lineStart: Int, lineEnd: Int): String {
    val newLineRange = String.format("L$lineStart-L$lineEnd")
    return String.format("${githubUrl.substringBefore("#")}#$newLineRange")
}


/**
 * This function will extract the suggestions from a ChatGPT output.
 * ChatGPT output may contain a mix of text (i.e. sentences) and code and Json.
 * The output is not always clean and this function is meant to identify the extract function suggestions
 *
 * Example of ChatGPT reply can be:
 *            I would suggest the following extract method refactorings:
 *
 *             1. Extract method for creating `GroupRebalanceConfig` object from lines 2650-2656.
 *             2. Extract method for creating `ConsumerCoordinator` object from lines 2649-2670.
 *             3. Extract method for creating `FetchConfig` object from lines 2674-2684.
 *             4. Extract method for creating `Fetcher` object from lines 2685-2692.
 *             5. Extract method for creating `OffsetFetcher` object from lines 2693-2701.
 *             6. Extract method for creating `TopicMetadataFetcher` object from lines 2702-2703.
 *
 *             The JSON object for these extract method refactorings would be:
 *
 *             ```
 *             [
 *                 {"function_name": "createRebalanceConfig", "line_start": 2650, "line_end": 2656},
 *                 {"function_name": "createConsumerCoordinator", "line_start": 2649, "line_end": 2670},
 *                 {"function_name": "createFetchConfig", "line_start": 2674, "line_end": 2684},
 *                 {"function_name": "createFetcher", "line_start": 2685, "line_end": 2692},
 *                 {"function_name": "createOffsetFetcher", "line_start": 2693, "line_end": 2701},
 *                 {"function_name": "createTopicMetadataFetcher", "line_start": 2702, "line_end": 2703}
 *             ]
 *             ```
 *
 * Another example may be:
 *             "The above function does not have any extract method opportunities."
 *
 */
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


fun isCandidateExtractable(efCandidate: EFCandidate, editor: Editor, file: PsiFile): Boolean {
    editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
    when(file.language) {
        JavaLanguage.INSTANCE -> return isFunctionExtractableJava(editor, file)
        KotlinLanguage.INSTANCE -> return isSelectionExtractableKotlin(editor, file)
    }
    return false
}

private fun isFunctionExtractableJava(
    editor: Editor,
    file: PsiFile
): Boolean {
    val range = ExtractMethodHelper.findEditorSelection(editor)
    val elements = ExtractSelector().suggestElementsToExtract(file, range!!)

    if (elements == null || elements.isEmpty()) {
        return false
    }

    var allOptionsToExtract = emptyList<ExtractOptions>()

    try {
        allOptionsToExtract = findAllOptionsToExtract(elements)
    } catch (e: Exception) {
        logException(e)
    }
    return allOptionsToExtract.isNotEmpty()
}

private fun isSelectionExtractableKotlin(
    editor: Editor,
    file: PsiFile
): Boolean {
    var res = false
    val efKotlinHandler = ExtractKotlinFunctionHandler()
    if (file !is KtFile) return false
    try {
        efKotlinHandler.selectElements(editor, file) { elements, _ ->
            res = elements.isNotEmpty()
        }
    } catch (e: Exception) {
        logException(e)
        res = false
    }
    return res
}

private fun logException(e: Exception) {
    val logger = Logger.getInstance("#com.intellij.ml.llm")
    val lineNumber = e.stackTrace.firstOrNull()?.lineNumber
    logger.info("Utils.kt:$lineNumber", e)
}