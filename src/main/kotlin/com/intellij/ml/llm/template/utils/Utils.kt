package com.intellij.ml.llm.template.utils

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EFSuggestionList
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline.findAllOptionsToExtract
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange


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
    var result = true
    var reason = ""
    var applicationResult = EFApplicationResult.OK

    if (!efCandidate.isValid()) {
        result = false
        reason = LLMBundle.message("extract.function.invalid.candidate")
        applicationResult = EFApplicationResult.FAIL
    } else if (selectionIsEntireBodyFunctionJava(efCandidate, file)) {
        result = false
        reason = LLMBundle.message("extract.function.entire.function.selection.message")
        applicationResult = EFApplicationResult.FAIL
    } else {
        editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
        val range = ExtractMethodHelper.findEditorSelection(editor)
        val elements = ExtractSelector().suggestElementsToExtract(file, range!!)
        if (elements.isEmpty()) {
            result = false
            applicationResult = EFApplicationResult.FAIL
            reason = LLMBundle.message("extract.function.code.not.extractable.message")
        } else {
            try {
                val allOptionsToExtract = findAllOptionsToExtract(elements)
                if (allOptionsToExtract.isEmpty()) {
                    result = false
                    reason = LLMBundle.message("extract.function.code.not.extractable.message")
                    applicationResult = EFApplicationResult.FAIL
                }
            } catch (e: Exception) {
                logException(e)
                result = false
                reason = e.message ?: reason
                applicationResult = EFApplicationResult.FAIL
            }
        }
    }
    editor.selectionModel.removeSelection()
    buildEFNotificationAndNotifyObservers(efCandidate, applicationResult, reason, observerList)
    return result
}

private fun isSelectionExtractableKotlin(
    efCandidate: EFCandidate,
    editor: Editor,
    file: PsiFile,
    observerList: List<Observer>
): Boolean {
    var result = true
    var reason = ""
    var applicationResult = EFApplicationResult.OK

    if (file !is KtFile) return false
    if (!efCandidate.isValid()) {
        result = false
        reason = LLMBundle.message("extract.function.invalid.candidate")
        applicationResult = EFApplicationResult.FAIL
    } else if (selectionIsEntireBodyFunctionKotlin(efCandidate, file)) {
        result = false
        reason = LLMBundle.message("extract.function.entire.function.selection.message")
        applicationResult = EFApplicationResult.FAIL
    } else if (!canSelectElementsForExtractionKotlin(efCandidate, editor, file)) {
        result = false
        reason = LLMBundle.message("extract.function.code.not.extractable.message")
        applicationResult = EFApplicationResult.FAIL
    } else {
        try {
            val elements = file.elementsInRange(TextRange(efCandidate.offsetStart, efCandidate.offsetEnd))
            val targetSibling = PsiUtils.getParentFunctionOrNull(elements[0], file.language)
            val extractionData = ExtractionData(file, elements.toRange(false), targetSibling!!)
            val analysisResult = extractionData.performAnalysis()
            if (analysisResult.status != AnalysisResult.Status.SUCCESS) {
                result = false
                reason = LLMBundle.message("extract.function.code.not.extractable.message")
                applicationResult = EFApplicationResult.FAIL
            } else {
                ExtractionGeneratorConfiguration(
                    analysisResult.descriptor!!,
                    ExtractionGeneratorOptions(
                        inTempFile = true,
                        target = ExtractionTarget.FUNCTION,
                        dummyName = efCandidate.functionName,
                    )
                ).generateDeclaration()
            }

        } catch (t: Throwable) {
            result = false
            reason = LLMBundle.message("extract.function.code.not.extractable.message")
            applicationResult = EFApplicationResult.FAIL
        }
    }

    buildEFNotificationAndNotifyObservers(efCandidate, applicationResult, reason, observerList)
    editor.selectionModel.removeSelection()
    return result
}

fun canSelectElementsForExtractionKotlin(efCandidate: EFCandidate, editor: Editor, file: KtFile): Boolean {
    var result = false
    editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
    try {
        ExtractKotlinFunctionHandler().selectElements(editor, file) { elements, _ ->
            result = elements.isNotEmpty()
        }
    }
    catch (t: Throwable) {
        return result
    }
    editor.selectionModel.removeSelection()
    return result
}

private fun buildEFNotificationAndNotifyObservers(
    efCandidate: EFCandidate,
    result: EFApplicationResult,
    reason: String,
    observers: List<Observer>
) {
    observers.forEach {
        it.update(
            EFNotification(
                EFCandidateApplicationPayload(
                    result = result,
                    candidate = efCandidate,
                    reason = reason
                )
            )
        )
    }
}

private fun logException(e: Exception) {
    val logger = Logger.getInstance("#com.intellij.ml.llm")
    val lineNumber = e.stackTrace.firstOrNull()?.lineNumber
    logger.info("Utils.kt:$lineNumber", e)
}

private fun selectionIsEntireBodyFunctionKotlin(efCandidate: EFCandidate, file: PsiFile): Boolean {
    val start = file.findElementAt(efCandidate.offsetStart)
    val end = file.findElementAt(efCandidate.offsetEnd)

    val parentBlock =
        PsiUtils.getParentFunctionBlockOrNull(start, KotlinLanguage.INSTANCE) ?: PsiUtils.getParentFunctionBlockOrNull(
            end,
            KotlinLanguage.INSTANCE
        )
    val statements = (parentBlock as KtBlockExpression).statements
    if (statements.isEmpty()) return false
    val statementsOffsetRange = statements.first().startOffset to statements.last().endOffset

    return efCandidate.offsetStart <= statementsOffsetRange.first && efCandidate.offsetEnd >= statementsOffsetRange.second
}

private fun selectionIsEntireBodyFunctionJava(efCandidate: EFCandidate, file: PsiFile): Boolean {
    val start = file.findElementAt(efCandidate.offsetStart)
    val end = file.findElementAt(efCandidate.offsetEnd)

    val parentBlock = PsiUtils.getParentFunctionBlockOrNull(start, JavaLanguage.INSTANCE)
        ?: PsiUtils.getParentFunctionBlockOrNull(end, JavaLanguage.INSTANCE)
    val statements = (parentBlock as PsiCodeBlock).statements

    if (statements.isEmpty()) {
        return false
    }

    val statementsOffsetRange = statements.first().startOffset to statements.last().endOffset
    return efCandidate.offsetStart <= statementsOffsetRange.first && efCandidate.offsetEnd >= statementsOffsetRange.second
}


class PsiUtils {
    companion object {
        fun getParentFunctionOrNull(psiElement: PsiElement?, language: Language?): PsiElement? {
            when (language) {
                JavaLanguage.INSTANCE -> return PsiTreeUtil.getParentOfType(psiElement, PsiMethod::class.java)
                KotlinLanguage.INSTANCE -> return PsiTreeUtil.getParentOfType(psiElement, KtNamedFunction::class.java)
            }
            return null
        }

        fun getParentFunctionOrNull(editor: Editor, language: Language?): PsiElement? {
            return getParentFunctionOrNull(PsiUtilBase.getElementAtCaret(editor), language)
        }

        fun getParentFunctionBlockOrNull(psiElement: PsiElement?, language: Language?): PsiElement? {
            if (psiElement == null) return null
            return getFunctionBlockOrNull(getParentFunctionOrNull(psiElement, language))
        }

        fun getFunctionBlockOrNull(psiElement: PsiElement?): PsiElement? {
            when (psiElement) {
                is PsiMethod -> return PsiTreeUtil.getChildOfType(psiElement, PsiCodeBlock::class.java)
                is KtNamedFunction -> return PsiTreeUtil.getChildOfType(psiElement, KtBlockExpression::class.java)
            }
            return null
        }

        fun getFunctionBodyStartLine(psiElement: PsiElement?): Int {
            val block = getFunctionBlockOrNull(psiElement)
            var startLine = -1
            if (block != null) {
                startLine = block.firstChild.getLineNumber(false) + 1
            }
            return startLine
        }

        fun getParentFunctionCallOrNull(psiElement: PsiElement, language: Language): PsiElement? {
            when (language) {
                KotlinLanguage.INSTANCE ->
                    return PsiTreeUtil.getTopmostParentOfType(psiElement, KtDotQualifiedExpression::class.java)
                        ?: PsiTreeUtil.getTopmostParentOfType(psiElement, KtCallExpression::class.java)
            }
            return null
        }

        fun elementsBelongToSameFunction(start: PsiElement, end: PsiElement, language: Language): Boolean {
            val parentFunctionStart = getParentFunctionOrNull(start, language) ?: return false
            val parentFunctionEnd = getParentFunctionOrNull(end, language) ?: return false
            return parentFunctionStart == parentFunctionEnd
        }

        fun isElementArgumentOrArgumentList(psiElement: PsiElement, language: Language): Boolean {
            when (language) {
                KotlinLanguage.INSTANCE -> {
                    return PsiTreeUtil.getTopmostParentOfType(psiElement, KtValueArgumentList::class.java) != null
                }

                JavaLanguage.INSTANCE -> {
                    return PsiTreeUtil.getTopmostParentOfType(psiElement, PsiExpressionList::class.java) != null
                }
            }
            return false
        }

        fun isElementParameterOrParameterList(psiElement: PsiElement?, language: Language): Boolean {
            when (language) {
                KotlinLanguage.INSTANCE -> {
                    val x = PsiTreeUtil.getTopmostParentOfType(psiElement, KtParameterList::class.java)
                    return x != null
                }

                JavaLanguage.INSTANCE -> {
                    return PsiTreeUtil.getTopmostParentOfType(psiElement, PsiParameterList::class.java) != null
                }
            }
            return false
        }
    }
}
