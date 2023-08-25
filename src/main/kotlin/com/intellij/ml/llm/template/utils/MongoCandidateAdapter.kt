package com.intellij.ml.llm.template.utils

import com.google.gson.Gson
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFMultishotCandidate
import com.intellij.ml.llm.template.models.LlmMultishotResponseData
import com.intellij.ml.llm.template.models.openai.OpenAIChatResponse
import org.bson.Document

class MongoCandidateAdapter {
    companion object {
        fun buildMultishotDocument(multishotCandidates: List<EFMultishotCandidate>): Document {
            val groupedMultishotCandidates = multishotCandidates.groupBy { it.tryNumber }
            val multishotCandidateDocsByShot = mutableMapOf<Int, List<Document>>()
            val llmResponseByShot = mutableMapOf<Int, String>()
            for ((shotNo, groupedMultishotCandidates) in groupedMultishotCandidates) {
                val openAIChatResponse = (groupedMultishotCandidates.first().llmRawResponse as OpenAIChatResponse)
                val openAiChatResponseJson = Gson().toJson(openAIChatResponse, OpenAIChatResponse::class.java)
                llmResponseByShot.put(shotNo, openAiChatResponseJson)

                val payloadList = groupedMultishotCandidates.flatMap { it.efCandidateApplicationPayloadList }
                val candidatesDocList = mutableListOf<Document>()
                payloadList.forEach {
                    val candidate = it.candidate
                    val applicationResult = it.result
                    val reason = it.reason
                    val githubUrl = ""
                    val extractedFunctionSnippet = ""

                    val candidateDoc = adaptCandidate(candidate, it)
                        .append("shot_no", shotNo)

                    candidatesDocList.add(candidateDoc)
                }
                multishotCandidateDocsByShot.put(shotNo, candidatesDocList)
            }

            val resultDocument = Document()
            for ((shot_no, doclist) in multishotCandidateDocsByShot) {
                resultDocument
                    .append(
                        "shot_$shot_no", Document()
                            .append("candidates", doclist)
                            .append("llm_raw_response", llmResponseByShot[shot_no])
                    )
            }
            return resultDocument
        }

        private fun adaptCandidate(
            candidate: EFCandidate,
            applicationPayload: EFCandidateApplicationPayload
        ): Document {
            val applicationResult = applicationPayload.result
            val reason = applicationPayload.reason

            val result = Document()
                .append("candidate_type", candidate.type)
                .append("application_result", applicationResult)
                .append("application_reason", reason)
                .append("function_name", candidate.functionName)
                .append("line_start", candidate.lineStart)
                .append("line_end", candidate.lineEnd)
                .append("length", candidate.length)
                .append("offset_start", candidate.offsetStart)
                .append("offset_end", candidate.offsetEnd)
            return result
        }

        private fun adaptRankedCandidate(
            candidate: EFCandidate,
            applicationPayload: EFCandidateApplicationPayload
        ): Document {
            val result = adaptCandidate(candidate, applicationPayload)
                .append("popularity", candidate.popularity)
                .append("heat", candidate.heat)
            return result
        }

        fun adaptRankedCandidateList(
            candidates: List<EFCandidate>,
            multishotCandidates: List<EFMultishotCandidate>
        ): List<Document> {
            val result = mutableListOf<Document>()
            candidates.forEach { candidate ->
                // find the EFCandidateApplicationPayload for the candidate
                for (multishotCandidate in multishotCandidates) {
                    val payload =
                        multishotCandidate.efCandidateApplicationPayloadList.find { it.candidate == candidate }
                    if (payload != null) {
                        result.add(adaptRankedCandidate(candidate, payload))
                        break
                    }
                }
            }

            return result
        }

        fun enrichWithGithubUrl(candidateDocs: List<Document>, githubUrl: String): List<Document> {
            val regexL = """#L\d+-L\d+""".toRegex()
            candidateDocs.forEach { candidateDoc ->
                val lineStart = candidateDoc.getInteger("line_start")
                val lineEnd = candidateDoc.getInteger("line_end")
                val adjustedGithubUrl = regexL.replace(githubUrl, "#L$lineStart-L$lineEnd")
                candidateDoc.append("github_url", adjustedGithubUrl)
            }
            return candidateDocs
        }

        fun enrichWithCodeSnippet(candidateDocsMap: Map<Document, String>): List<Document> {
            return candidateDocsMap.keys.toList()
        }

        fun llmMultishotResponseData2Mongo(llmMultishotResponseDataList: List<LlmMultishotResponseData>): List<Document> {
            val grouped = llmMultishotResponseDataList.groupBy { it.shotNo }
            val result = mutableListOf<Document>()
            for ((shotNo, multishotResponseDataList) in grouped) {
                if (multishotResponseDataList.isEmpty()) continue
                val multishotResponseData = multishotResponseDataList[0]
                if (multishotResponseData.llmResponse == null) continue
                val openAIChatResponse = multishotResponseData.llmResponse as OpenAIChatResponse
                val currentShotDoc = Document()
                    .append("llm_raw_response", Gson().toJson(openAIChatResponse, OpenAIChatResponse::class.java))
                    .append("llm_processing_time", multishotResponseData.processingTime)
                    .append("shot_no", shotNo)
                result.add(currentShotDoc)
            }
            return result
        }

        fun mongo2LLMMultishotResponseData(documents: List<Document>) : List<LlmMultishotResponseData> {
            val llmMultishotResponseDataList = mutableListOf<LlmMultishotResponseData>()
            documents.forEach { document ->
                val llmMultishotResponseData = LlmMultishotResponseData(
                    shotNo = document.getInteger("shot_no"),
                    processingTime = document.getLong("llm_processing_time"),
                    llmResponse = Gson().fromJson(document.getString("llm_raw_response"), OpenAIChatResponse::class.java)
                )
                llmMultishotResponseDataList.add(llmMultishotResponseData)
            }

            return llmMultishotResponseDataList.sortedBy { it.shotNo }
        }
    }
}