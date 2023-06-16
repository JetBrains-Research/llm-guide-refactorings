package com.intellij.ml.llm.template.telemetry

import com.google.gson.annotations.SerializedName
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EfCandidateType
import com.intellij.ml.llm.template.utils.EFApplicationResult
import com.intellij.ml.llm.template.utils.EFCandidateApplicationPayload
import java.util.*

data class EFTelemetryData(
    @SerializedName("id")
    var id: String,
) {
    @SerializedName("hostFunctionTelemetryData")
    lateinit var hostFunctionTelemetryData: EFHostFunctionTelemetryData

    @SerializedName("candidatesTelemetryData")
    lateinit var candidatesTelemetryData: EFCandidatesTelemetryData

    @SerializedName("userSelectionTelemetryData")
    lateinit var userSelectionTelemetryData: EFUserSelectionTelemetryData
}

data class EFHostFunctionTelemetryData(
    @SerializedName("hostFunctionSize")
    var hostFunctionSize: Int,

    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,

    @SerializedName("bodyLineStart")
    var bodyLineStart: Int,
)

data class EFCandidatesTelemetryData(
    @SerializedName("numberOfSuggestions")
    var numberOfSuggestions: Int,

    @SerializedName("candidates")
    var candidates: List<EFCandidateTelemetryData>,
)

data class EFCandidateTelemetryData(
    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,

    @SerializedName("candidateType")
    var candidateType: EfCandidateType,

    @SerializedName("applicationResult")
    var applicationResult: EFApplicationResult,

    @SerializedName("reason")
    var reason: String
)

data class EFUserSelectionTelemetryData(
    @SerializedName("lineStart")
    var lineStart: Int,

    @SerializedName("lineEnd")
    var lineEnd: Int,

    @SerializedName("functionSize")
    var functionSize: Int,

    @SerializedName("positionInHostFunction")
    var positionInHostFunction: Int,

    @SerializedName("selectedCandidateIndex")
    var selectedCandidateIndex: Int,

    @SerializedName("candidateType")
    var candidateType: EfCandidateType,
)

class EFTelemetryDataManager {
    private var currentSessionId: String = ""
    private val data: MutableMap<String, EFTelemetryData> = mutableMapOf()
    private lateinit var currentTelemetryData: EFTelemetryData

    fun newSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        currentTelemetryData = EFTelemetryData(currentSessionId)
        data[currentSessionId] = currentTelemetryData
        return currentSessionId
    }

    fun currentSession(): String {
        if (currentSessionId.isNotEmpty()) return currentSessionId
        return newSession()
    }

    fun addHostFunctionTelemetryData(hostFunctionTelemetryData: EFHostFunctionTelemetryData): EFTelemetryDataManager {
        currentTelemetryData.hostFunctionTelemetryData = hostFunctionTelemetryData
        return this
    }

    fun addCandidatesTelemetryData(candidatesTelemetryData: EFCandidatesTelemetryData): EFTelemetryDataManager {
        currentTelemetryData.candidatesTelemetryData = candidatesTelemetryData
        return this
    }

    fun addUserSelectionTelemetryData(userSelectionTelemetryData: EFUserSelectionTelemetryData): EFTelemetryDataManager {
        currentTelemetryData.userSelectionTelemetryData = userSelectionTelemetryData
        return this
    }

    fun getData(sessionId: String? = null): EFTelemetryData? {
        val sId = sessionId ?: currentSession()
        return data.getOrDefault(sId, null)
    }
}

class EFTelemetryDataUtils {
    companion object {
        fun buildHostFunctionTelemetryData(codeSnippet: String, lineStart: Int, bodyLineStart: Int): EFHostFunctionTelemetryData {
            val functionSize = codeSnippet.lines().size
            return EFHostFunctionTelemetryData(
                lineStart = lineStart,
                lineEnd = lineStart + functionSize - 1,
                hostFunctionSize = functionSize,
                bodyLineStart = bodyLineStart
            )
        }

        private fun buildCandidateTelemetryData(candidateApplicationPayload: EFCandidateApplicationPayload): EFCandidateTelemetryData {
            val candidate = candidateApplicationPayload.candidate
            return EFCandidateTelemetryData(
                lineStart = candidate.lineStart,
                lineEnd = candidate.lineEnd,
                candidateType = candidate.type,
                applicationResult = candidateApplicationPayload.result,
                reason = candidateApplicationPayload.reason
            )
        }

        fun buildCandidateTelemetryData(candidateApplicationPayloadList: List<EFCandidateApplicationPayload>): List<EFCandidateTelemetryData> {
            val candidateTelemetryDataList: MutableList<EFCandidateTelemetryData> = mutableListOf()
            candidateApplicationPayloadList.forEach {
                candidateTelemetryDataList.add(buildCandidateTelemetryData(it))
            }
            return candidateTelemetryDataList.toList()
        }

        fun buildUserSelectionTelemetryData(
            efCandidate: EFCandidate,
            candidateIndex: Int,
            hostFunctionTelemetryData: EFHostFunctionTelemetryData?,
        ): EFUserSelectionTelemetryData {
            var positionInHostFunction = -1
            if (hostFunctionTelemetryData != null) {
                positionInHostFunction = efCandidate.lineStart - hostFunctionTelemetryData.bodyLineStart
            }
            return EFUserSelectionTelemetryData(
                lineStart = efCandidate.lineStart,
                lineEnd = efCandidate.lineEnd,
                functionSize = efCandidate.lineEnd - efCandidate.lineStart + 1,
                positionInHostFunction = positionInHostFunction,
                selectedCandidateIndex = candidateIndex,
                candidateType = efCandidate.type
            )
        }
    }
}