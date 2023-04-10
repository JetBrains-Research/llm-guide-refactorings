package com.intellij.ml.llm.template.intentions

import com.google.gson.Gson
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.models.*
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import kotlinx.serialization.json.Json
import org.jsoup.SerializationException


@Suppress("UnstableApiUsage")
abstract class ApplyExtractFunctionTransformationIntention(
    private val llmRequestProvider: LLMRequestProvider = GPTRequestProvider
) : IntentionAction {
    private val logger = Logger.getInstance("#com.intellij.ml.llm")

    override fun getFamilyName(): String = LLMBundle.message("intentions.apply.transformation.family.name")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file != null
    }

    private fun findSelectedPsiElements(editor: Editor?, file: PsiFile?): Array<PsiElement> {
        if (editor == null) {
            return emptyArray()
        }
        val selectionModel = editor.selectionModel
        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd

        val startElement = file?.findElementAt(startOffset)
        val endElement = file?.findElementAt(if (endOffset > 0) endOffset - 1 else endOffset)

        if (startElement == null || endElement == null) {
            return emptyArray()
        }

        val commonParent = PsiTreeUtil.findCommonParent(startElement, endElement)
        if (commonParent == null) {
            return emptyArray()
        }

        val selectedElements = PsiTreeUtil.findChildrenOfType(commonParent, PsiElement::class.java)
        return selectedElements.filter {
            it.textRange.startOffset >= startOffset && it.textRange.endOffset <= endOffset
        }.toTypedArray()
    }

    private fun invokeExtractFunction(efs: EFSuggestion, project: Project, editor: Editor?, file: PsiFile?) {
        MyMethodExtractor.invokeOnElements(
            project, editor, file, findSelectedPsiElements(editor, file), FunctionNameProvider(efs.functionName)
        )
    }

    private fun selectLines(startLine: Int, endLine: Int, editor: Editor?) {
        if (editor == null || editor.document == null) return
        val selectionModel = editor.selectionModel
        selectionModel.setSelection(
            editor.document.getLineStartOffset(startLine), editor.document.getLineEndOffset(endLine)
        )
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

        val document = editor.document
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText
        if (selectedText != null) {
            /*
             * The selected text should be the whole function. We have to follow the following steps:
             * 1. Take the selected text, and send it to ChatGPT using a well formatted prompt
             * 2. Process the reply
             *      - how to handle multiple suggestions? Take the largest?
             *      - options should be filtered based on some criteria such as how actionable they are,
             *        how many lines of code do they cover, etc
             * 3. Present suggestions to developer and let the developer choose
             * 4. Based on the developer's choice, automatic Extract Function should be performed
             */
            invokeLlm(selectedText, project, editor, file)
        } else {
            val namedElement = getParentNamedElement(editor)
            if (namedElement != null) {
                val codeSnippet = namedElement.text
                val textRange = namedElement.textRange
                selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
                val startLineNumber = editor.document.getLineNumber(selectionModel.selectionStart)
                val withLineNumbers = addLineNumbersToCodeSnippet(codeSnippet, startLineNumber)
                invokeLlm(withLineNumbers, project, editor, file)
            } else {
                selectionModel.selectLineAtCaret()
                val textRange = getLineTextRange(document, editor)
//                transform(project, document.getText(textRange), editor, textRange)
            }
        }
    }

    private fun addLineNumbersToCodeSnippet(codeSnippet: String, startIndex: Int): String {
        val lines = codeSnippet.lines()
        val numberedLines = lines.mapIndexed { index, line -> "${startIndex + index}. $line" }
        return numberedLines.joinToString("\n")
    }

    private fun extractJsonSubstring(input: String): String? {
        val jsonPattern = "\\{[^{}]*}".toRegex()
        val potentialJsonObjects = jsonPattern.findAll(input)
        for (match in potentialJsonObjects) {
            val jsonString = match.value
            try {
                Json.parseToJsonElement(jsonString)
                return jsonString
            } catch (e: SerializationException) {
                // Ignoring invalid Json string
            }
        }

        return null
    }

    private fun invokeLlm(text: String, project: Project, editor: Editor, file: PsiFile) {
        logger.info("Invoking LLM with text: $text")
        val messageList = fewShotExtractSuggestion(text)

        val task = object : Task.Backgroundable(
            project, LLMBundle.message("intentions.request.extract.function.background.process.title")
        ) {
            override fun run(indicator: ProgressIndicator) {
                val response = sendChatRequest(
                    project, messageList, llmRequestProvider.chatModel, llmRequestProvider
                )
                if (response != null) {
                    logger.info("Full response:\n${response.toString()}")
                    response.getSuggestions().firstOrNull()?.let {
                        invokeLater {
                            val jsonText = extractJsonSubstring(it.text)
                            if (jsonText != null) {
                                logger.info(jsonText)
                                val efs = Gson().fromJson(jsonText, EFSuggestion::class.java)
                                applySuggestion(efs, project, editor, file)
                            }
                        }
                    }
                }
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    }

    private fun applySuggestion(efSuggestion: EFSuggestion, project: Project, editor: Editor, file: PsiFile) {
        selectLines(efSuggestion.lineStart, efSuggestion.lineEnd, editor)
        invokeExtractFunction(efSuggestion, project, editor, file)
    }

    private fun getLineTextRange(document: Document, editor: Editor): TextRange {
        val lineNumber = document.getLineNumber(editor.caretModel.offset)
        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)
        return TextRange.create(startOffset, endOffset)
    }

    private fun getParentNamedElement(editor: Editor): PsiNameIdentifierOwner? {
        val element = PsiUtilBase.getElementAtCaret(editor)
        return PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
    }

    abstract fun getInstruction(project: Project, editor: Editor): String?

    override fun startInWriteAction(): Boolean = false
}