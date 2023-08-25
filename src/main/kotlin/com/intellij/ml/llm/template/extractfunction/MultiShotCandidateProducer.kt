package com.intellij.ml.llm.template.extractfunction

import com.intellij.ml.llm.template.models.LlmMultishotResponseData
import com.intellij.ml.llm.template.utils.EFCandidateFactory
import com.intellij.ml.llm.template.utils.EFCandidatesApplicationTelemetryObserver
import com.intellij.ml.llm.template.utils.identifyExtractFunctionSuggestions
import com.intellij.ml.llm.template.utils.isCandidateExtractable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.util.concurrent.TimeUnit

class MultiShotCandidateProducer(val project: Project, val editor: Editor, val file: PsiFile) {
    fun buildMultishotCandidates(llmMultishotResponseDataList: List<LlmMultishotResponseData>): List<EFMultishotCandidate> {
        val multishotCandidates = mutableListOf<EFMultishotCandidate>()
        val groupedData = llmMultishotResponseDataList.groupBy { it.shotNo }
        for ((shotNo, multishotResponseDataList) in groupedData) {
            if (multishotResponseDataList.isEmpty()) continue
            val multishotResponseData = multishotResponseDataList[0]
            val candidateApplicationObserver = EFCandidatesApplicationTelemetryObserver()
            val llmRawResponse = multishotResponseData.llmResponse
            if (llmRawResponse == null) continue
            if (llmRawResponse.getSuggestions().isEmpty()) continue
            val llmResponse = llmRawResponse.getSuggestions()[0]

            val startTime = System.nanoTime()

            val efSuggestionList = identifyExtractFunctionSuggestions(llmResponse.text)
            val candidates =
                EFCandidateFactory().buildCandidates(efSuggestionList.suggestionList, editor, file).toList()
            val filteredCandidates = candidates.filter {
                isCandidateExtractable(it, editor, file, listOf(candidateApplicationObserver))
            }

            val jetGPTProcessingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)

            val multishotCandidate = EFMultishotCandidate(
                efCandidates = filteredCandidates,
                efCandidateApplicationPayloadList = candidateApplicationObserver.getData(),
                llmRawResponse = llmRawResponse,
                llmProcessingTime = multishotResponseData.processingTime,
                jetGPTProcessingTime = jetGPTProcessingTime,
                tryNumber = shotNo
            )
            multishotCandidates.add(multishotCandidate)
        }

        return multishotCandidates
    }
}