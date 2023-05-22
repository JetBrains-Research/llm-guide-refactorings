package com.intellij.ml.llm.template.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.GPTRequestProvider
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.sendChatRequest
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.ml.llm.template.utils.CodeTransformer
import com.intellij.ml.llm.template.utils.EFCandidateFactory
import com.intellij.ml.llm.template.utils.addLineNumbersToCodeSnippet
import com.intellij.ml.llm.template.utils.identifyExtractFunctionSuggestions
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase


@Suppress("UnstableApiUsage")
abstract class ApplyExtractFunctionTransformationIntention(
    private val llmRequestProvider: LLMRequestProvider = GPTRequestProvider,
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
) : IntentionAction {
    private val logger = Logger.getInstance("#com.intellij.ml.llm")
    private val codeTransformer = CodeTransformer()

    override fun getFamilyName(): String = LLMBundle.message("intentions.apply.transformation.family.name")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return editor != null && file != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return

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
            }
        }
    }

    private fun invokeLlm(text: String, project: Project, editor: Editor, file: PsiFile) {
        logger.info("Invoking LLM with text: $text")
        val messageList = fewShotExtractSuggestion(text)

        val task = object : Task.Backgroundable(
            project, LLMBundle.message("intentions.request.extract.function.background.process.title")
        ) {
            override fun run(indicator: ProgressIndicator) {
                val response = sendChatRequest(
                    project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
                )
                if (response != null) {
                    invokeLater {
                        val efCandidateFactory = EFCandidateFactory()
                        response.getSuggestions().forEach { suggestion ->
                            val efSuggestionList = identifyExtractFunctionSuggestions(suggestion.text)
                            val efCandidates = ArrayList<EFCandidate>()
                            efSuggestionList.suggestion_list.forEach{ efs ->
                                efCandidates.addAll(efCandidateFactory.buildCandidates(efs, editor, file))
                            }
                            efCandidates.take(1).forEach { candidate ->
                                codeTransformer.applyCandidate(candidate, project, editor, file)
                            }
                        }
                    }
                }
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    }

    private fun getParentNamedElement(editor: Editor): PsiNameIdentifierOwner? {
        val element = PsiUtilBase.getElementAtCaret(editor)
        return PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
    }

    abstract fun getInstruction(project: Project, editor: Editor): String?

    override fun startInWriteAction(): Boolean = false
}