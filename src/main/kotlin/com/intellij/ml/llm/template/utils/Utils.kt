package com.intellij.ml.llm.template.utils

import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EFSuggestionList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.findAllOptionsToExtract
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction


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


fun isCandidateExtractable(
    efCandidate: EFCandidate,
    editor: Editor,
    file: PsiFile,
    observerList: List<Observer> = emptyList()
): Boolean {
    when (file.language) {
        JavaLanguage.INSTANCE -> return isFunctionExtractableJava(efCandidate, editor, file, observerList)
        KotlinLanguage.INSTANCE -> return isSelectionExtractableKotlin(efCandidate, editor, file, observerList)
    }
    return false
}

private fun isFunctionExtractableJava(
    efCandidate: EFCandidate,
    editor: Editor,
    file: PsiFile,
    observerList: List<Observer>
): Boolean {
    editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
    val range = ExtractMethodHelper.findEditorSelection(editor)
    val elements = ExtractSelector().suggestElementsToExtract(file, range!!)

    if (selectionIsEntireBodyFunctionJava(efCandidate, editor, file)) {
        observerList.forEach {
            it.update(
                EFNotification(
                    EFCandidateApplicationPayload(
                        result = EFApplicationResult.FAIL,
                        candidate = efCandidate,
                        reason = LLMBundle.message("extract.function.entire.function.selection.message"),
                    )
                )
            )
        }
        editor.selectionModel.removeSelection()
        return false
    }

    if (elements.isEmpty()) {
        observerList.forEach {
            it.update(
                EFNotification(
                    EFCandidateApplicationPayload(
                        result = EFApplicationResult.FAIL,
                        candidate = efCandidate,
                        reason = LLMBundle.message("extract.function.code.not.extractable.message"),
                    )
                )
            )
        }
        editor.selectionModel.removeSelection()
        return false
    }

    val allOptionsToExtract: List<ExtractOptions>
    var reason = ""

    try {
        allOptionsToExtract = findAllOptionsToExtract(elements)
    } catch (e: Exception) {
        logException(e)
        reason = e.message ?: reason
        observerList.forEach {
            it.update(
                EFNotification(
                    EFCandidateApplicationPayload(
                        result = EFApplicationResult.FAIL,
                        candidate = efCandidate,
                        reason = reason
                    )
                )
            )
        }
        editor.selectionModel.removeSelection()
        return false
    }


    observerList.forEach {
        it.update(
            EFNotification(
                EFCandidateApplicationPayload(
                    result = if (allOptionsToExtract.isNotEmpty()) EFApplicationResult.OK else EFApplicationResult.FAIL,
                    candidate = efCandidate,
                    reason = reason
                )
            )
        )
    }
    editor.selectionModel.removeSelection()

    return allOptionsToExtract.isNotEmpty()
}

private fun isSelectionExtractableKotlin(
    efCandidate: EFCandidate,
    editor: Editor,
    file: PsiFile,
    observerList: List<Observer>
): Boolean {
    editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
    var res = false
    var reason = ""

    if (selectionIsEntireBodyFunctionKotlin(efCandidate, editor, file)) {
        observerList.forEach {
            it.update(
                EFNotification(
                    EFCandidateApplicationPayload(
                        result = EFApplicationResult.FAIL,
                        candidate = efCandidate,
                        reason = LLMBundle.message("extract.function.entire.function.selection.message")
                    )
                )
            )
        }
        editor.selectionModel.removeSelection()
        return false
    }

    val efKotlinHandler = ExtractKotlinFunctionHandler()
    if (file !is KtFile) return false
    try {
        efKotlinHandler.selectElements(editor, file) { elements, _ ->
            res = elements.isNotEmpty()
        }
    } catch (e: Exception) {
        logException(e)
        res = false
        reason = e.message ?: reason
    }

    observerList.forEach {
        it.update(
            EFNotification(
                EFCandidateApplicationPayload(
                    result = if (res) EFApplicationResult.OK else EFApplicationResult.FAIL,
                    candidate = efCandidate,
                    reason = reason
                )
            )
        )
    }
    editor.selectionModel.removeSelection()
    return res
}

private fun logException(e: Exception) {
    val logger = Logger.getInstance("#com.intellij.ml.llm")
    val lineNumber = e.stackTrace.firstOrNull()?.lineNumber
    logger.info("Utils.kt:$lineNumber", e)
}

private fun selectionIsEntireBodyFunctionKotlin(efCandidate: EFCandidate, editor: Editor, file: PsiFile): Boolean {
    fun findParentFunctionBlock(psiElement: PsiElement?): KtBlockExpression? {
        var result = psiElement

        while (result != null) {
            if ((result is KtDeclaration && result is KtNamedFunction)) {
                return result.bodyBlockExpression
            }
            result = result.parent
        }
        return result
    }

    val start = file.findElementAt(efCandidate.offsetStart)
    val end = file.findElementAt(efCandidate.offsetEnd)

    val parentFunction = findParentFunctionBlock(start) ?: findParentFunctionBlock(end)
    val statements = parentFunction?.statements ?: emptyList()
    if (statements.isEmpty()) return false
    val statementsOffsetRange = statements.first().startOffset to statements.last().endOffset

    return efCandidate.offsetStart <= statementsOffsetRange.first && efCandidate.offsetEnd >= statementsOffsetRange.second
}

private fun selectionIsEntireBodyFunctionJava(efCandidate: EFCandidate, editor: Editor, file: PsiFile): Boolean {
    fun findParentFunctionBlock(psiElement: PsiElement?): PsiCodeBlock? {
        var result = psiElement

        while (result != null) {
            if (result is PsiMethod) {
                return result.body
            }
            result = result.parent
        }
        return result
    }

    val start = file.findElementAt(efCandidate.offsetStart)
    val end = file.findElementAt(efCandidate.offsetEnd)

    val parentFunction = findParentFunctionBlock(start) ?: findParentFunctionBlock(end)
    val statements = parentFunction?.statements ?: emptyArray()

    if (statements.isEmpty()) {
        return false
    }

    val statementsOffsetRange = statements.first().startOffset to statements.last().endOffset
    return efCandidate.offsetStart <= statementsOffsetRange.first && efCandidate.offsetEnd >= statementsOffsetRange.second
}
