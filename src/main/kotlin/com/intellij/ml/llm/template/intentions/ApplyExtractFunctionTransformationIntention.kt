package com.intellij.ml.llm.template.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMBaseResponse
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.sendChatRequest
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.ml.llm.template.showEFNotification
import com.intellij.ml.llm.template.ui.ExtractFunctionPanel
import com.intellij.ml.llm.template.utils.*
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import java.awt.Point
import java.util.concurrent.atomic.AtomicReference


@Suppress("UnstableApiUsage")
abstract class ApplyExtractFunctionTransformationIntention(
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
) : IntentionAction {
    private val logger = Logger.getInstance("#com.intellij.ml.llm")
    private val codeTransformer = CodeTransformer()

    init {
        codeTransformer.addObserver(EFLoggerObserver(logger))
    }

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
             *        how many lines of code do they cover, etc.
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
                val startLineNumber = editor.document.getLineNumber(selectionModel.selectionStart) + 1
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
                        if (response.getSuggestions().isEmpty()) {
                            showEFNotification(
                                project,
                                LLMBundle.message("notification.extract.function.with.llm.no.suggestions.message"),
                                NotificationType.INFORMATION
                            )
                        } else {
                            processSuggestions(response, editor, file, project)
                        }
                    }
                }
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
    }

    private fun processSuggestions(
        response: LLMBaseResponse,
        editor: Editor,
        file: PsiFile,
        project: Project
    ) {
        val efCandidateFactory = EFCandidateFactory()
        for (suggestion in response.getSuggestions()) {
            val efSuggestionList = identifyExtractFunctionSuggestions(suggestion.text)
            val efCandidates = ArrayList<EFCandidate>()
            efSuggestionList.suggestionList.forEach { efs ->
                efCandidates.addAll(efCandidateFactory.buildCandidates(efs, editor, file))
            }

            if (efCandidates.isEmpty()) {
                showEFNotification(
                    project,
                    LLMBundle.message("notification.extract.function.with.llm.no.suggestions.message"),
                    NotificationType.INFORMATION
                )
            } else {
                val filteredCandidates = efCandidates.filter {
                    isCandidateExtractable(
                        it,
                        editor,
                        file,
                        EFLoggerObserver(logger)
                    )
                }
                if (filteredCandidates.isEmpty()) {
                    showEFNotification(
                        project,
                        LLMBundle.message("notification.extract.function.with.llm.no.extractable.candidates.message"),
                        NotificationType.INFORMATION
                    )
                } else {
                    showExtractFunctionPopup(project, editor, file, filteredCandidates, codeTransformer)
                }
            }
        }
    }

    private fun showExtractFunctionPopup(
        project: Project,
        editor: Editor,
        file: PsiFile,
        candidates: List<EFCandidate>,
        codeTransformer: CodeTransformer
    ) {
        val highlighter = AtomicReference(ScopeHighlighter(editor))
        val efPanel = ExtractFunctionPanel(
            project = project,
            editor = editor,
            file = file,
            candidates = candidates,
            codeTransformer = codeTransformer,
            highlighter = highlighter
        )
        val panel = efPanel.createPanel()

        // Create the popup
        val efPopup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, efPanel.myExtractFunctionsCandidateTable)
            .setRequestFocus(true)
            .setTitle(LLMBundle.message("ef.candidates.popup.title"))
            .setResizable(true)
            .setMovable(true)
            .createPopup()

        // Add onClosed listener
        efPopup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                highlighter.getAndSet(null).dropHighlight()
            }
        })

        // set the popup as delegate to the Extract Function panel
        efPanel.setDelegatePopup(efPopup)

        // Get the current editor and calculate the point at the top right corner
        val currentEditor = EditorFactory.getInstance().allEditors[0]
        val topRightPoint = Point(currentEditor.component.width - panel.preferredSize.width, 0)

        // Show the popup at the top right corner of the current editor
        efPopup.show(RelativePoint(currentEditor.component, topRightPoint))
    }

    private fun getParentNamedElement(editor: Editor): PsiNameIdentifierOwner? {
        val element = PsiUtilBase.getElementAtCaret(editor)
        return PsiTreeUtil.getParentOfType(element, PsiNameIdentifierOwner::class.java)
    }


    abstract fun getInstruction(project: Project, editor: Editor): String?

    override fun startInWriteAction(): Boolean = false
}