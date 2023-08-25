package com.intellij.ml.llm.template.extractfunction

import com.intellij.ml.llm.template.models.LLMBaseResponse
import com.intellij.ml.llm.template.utils.EFCandidateApplicationPayload

data class EFMultishotCandidate(
    val efCandidates: List<EFCandidate>,
    val efCandidateApplicationPayloadList: List<EFCandidateApplicationPayload>,
    val llmRawResponse: LLMBaseResponse?,
    val llmProcessingTime: Long,
    val jetGPTProcessingTime: Long,
    val tryNumber: Int
)