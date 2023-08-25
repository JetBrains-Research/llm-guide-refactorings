package evaluation

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.models.GPTExtractFunctionRequestProvider
import com.intellij.ml.llm.template.models.LLMBaseResponse
import com.intellij.ml.llm.template.models.LLMRequestProvider
import com.intellij.ml.llm.template.models.sendChatRequest
import com.intellij.ml.llm.template.prompts.fewShotExtractSuggestion
import com.intellij.ml.llm.template.utils.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.util.concurrent.TimeUnit

class JetGPTCandidatesProducer {
    private val efLLMRequestProvider: LLMRequestProvider = GPTExtractFunctionRequestProvider
    internal var llmProcessingTime = 0L
    internal var jetGPTProcessingTime = 0L
    internal var llmRawResponse: LLMBaseResponse? = null

    fun produceCandidates(filename: String,
                          lineStart: Int,
                          lineEnd: Int,
                          project: Project,
                          editor: Editor,
                          psiFile: PsiFile,
                          efCandidateApplicationObserver: EFCandidatesApplicationTelemetryObserver
    ) : Iterable<EFCandidate> {
        // read code snippet from file
        val codeSnippet = addLineNumbersToCodeSnippet(readCodeSnippet(filename, lineStart-1, lineEnd-1), lineStart)

        // get prompt
        val messageList = fewShotExtractSuggestion(codeSnippet)

        // communicate with ChatGPT
        var startTime = System.nanoTime()
        val llmRawResponse = sendChatRequest(
            project, messageList, efLLMRequestProvider.chatModel, efLLMRequestProvider
        )
        val llmProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

        // postprocess ChatGPT reply
        startTime = System.nanoTime()
        var filteredCandidates = emptyList<EFCandidate>()

        if (llmRawResponse == null) return filteredCandidates
        if (llmRawResponse!!.getSuggestions().isEmpty()) return filteredCandidates

        val llmResponse = llmRawResponse!!.getSuggestions()[0]
        val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.text)
        val candidates =
            EFCandidateFactory().buildCandidates(efSuggestionList.suggestionList, editor, psiFile).toList()
        filteredCandidates = candidates.filter {
            isCandidateExtractable(it, editor, psiFile, listOf(efCandidateApplicationObserver))
        }.sortedByDescending { it.lineEnd - it.lineStart }
        jetGPTProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

        return filteredCandidates
    }
}