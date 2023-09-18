package com.intellij.ml.llm.template.utils

import com.intellij.ml.llm.template.evaluation.HostFunctionData
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.psi.PsiElement

class EFCandidateUtils {
    companion object {
        fun computePopularity(candidates: List<EFCandidate>): List<EFCandidate> {
            val occureceMap = candidates.groupBy { Pair(it.lineStart, it.lineEnd) }
            occureceMap.forEach {
                for (candidate in it.value) {
                    candidate.popularity = it.value.size
                }
            }
            return candidates.distinctBy { Pair(it.lineStart, it.lineEnd) }
        }

        fun computeHeat(candidates: List<EFCandidate>, hostFunctionData: HostFunctionData): List<EFCandidate> {
            candidates.map { it.heat = 0 }
            val heatMap = mutableMapOf<Int/*line number*/, Int/*heat*/>()
            for (lineNo in hostFunctionData.lineStart..hostFunctionData.lineEnd) {
                heatMap[lineNo] = 0
            }

            candidates.forEach { candidate ->
                for (lineNo in candidate.lineStart..candidate.lineEnd) {
                    if (lineNo in heatMap) heatMap[lineNo] = heatMap[lineNo]!! + 1
                }
            }

            candidates.forEach { candidate ->
                val keysToSum = (candidate.lineStart..candidate.lineEnd).toSet()
                val heat = heatMap.filterKeys { it in keysToSum }.values.sum()
                candidate.heat = heat
            }

            return candidates
        }

        fun computeOverlap(candidates: List<EFCandidate>): List<EFCandidate> {
            candidates.map { it.overlap = 0 }
            for (ci in candidates.indices) {
                val currentCandidate = candidates.get(ci)
                for (oi in candidates.indices) {
                    if (oi == ci) continue
                    val otherCandidate = candidates.get(oi)
                    val ciStart = currentCandidate.lineStart
                    val ciEnd = currentCandidate.lineEnd
                    val oiStart = otherCandidate.lineStart
                    val oiEnd = otherCandidate.lineEnd
                    val overlap = maxOf(0, minOf(ciEnd, oiEnd) - maxOf(ciStart, oiStart) + 1)
                    currentCandidate.overlap += overlap
                }
            }

            return candidates
        }

        fun rankByPopularity(candidates: List<EFCandidate>): List<EFCandidate> {
            var resultCandidates = computePopularity(candidates)
            resultCandidates = resultCandidates.sortedByDescending { it.popularity }.distinctBy { it }

            // move one liners at the end
            val (matchingObjects, remainingObjects) = resultCandidates.partition { it.length == 1 }
            resultCandidates = remainingObjects + matchingObjects

            return resultCandidates
        }

        fun rankByHeat(candidates: List<EFCandidate>, hostFunctionData: HostFunctionData): List<EFCandidate> {
            val heatCap = 20
            var ranked = computePopularity(candidates)
//            ranked = computeHeat(candidates, hostFunctionData).distinctBy { listOf(it.lineStart, it.lineEnd) }
//                .sortedByDescending { minOf(heatCap, minOf(heatCap, it.heat)) * it.popularity }
            ranked = computeHeat(candidates, hostFunctionData).distinctBy { listOf(it.lineStart, it.lineEnd) }
                .sortedByDescending { it.heat * it.popularity }
            // move one liners at the end
            val (matchingObjects, remainingObjects) = ranked.partition { it.length == 1 }
            ranked = remainingObjects + matchingObjects

            return ranked
        }

        fun rankBySize(candidates: List<EFCandidate>): List<EFCandidate> {
            return candidates.distinctBy { listOf(it.lineStart, it.lineEnd) }.sortedByDescending { it.length }
        }

        fun rankByOverlap(candidates: List<EFCandidate>): List<EFCandidate> {
            var rankedCandidates = computeOverlap(candidates)
            rankedCandidates = rankedCandidates.distinctBy { listOf(it.lineStart, it.lineEnd) }.sortedByDescending { it.overlap }
//            var rankedCandidates = computePopularity(candidates)
//            rankedCandidates.map { it.popularity = (-1 * it.popularity) }
//            rankedCandidates = rankedCandidates.distinctBy { listOf(it.lineStart, it.lineEnd) }.sortedWith(
//                compareBy<EFCandidate>(
//                    { it.overlap },
//                    { it.popularity })
//            )
//            rankedCandidates.map { it.popularity = (-1 * it.popularity) }
            return rankedCandidates
        }

        fun candidateSizeIsAboveThreshold(candidate: EFCandidate, hostFunction: PsiElement?): Boolean {
            val threshold = 0.60
            if (hostFunction == null) return false
            val linesInFunctionBody = PsiUtils.countLinesInMethodBody(hostFunction)
            val linesInCandidate = candidate.lineEnd - candidate.lineStart + 1
            val pc: Double = (linesInCandidate.toDouble() / linesInFunctionBody)
            val result = pc > threshold
            return result
        }

        fun candidateSizeIsBelowThreshold(candidate: EFCandidate, hostFunction: PsiElement?): Boolean {
            val threshold = 0.14
            if (hostFunction == null) return false
            val linesInFunctionBody = PsiUtils.countLinesInMethodBody(hostFunction)
            val linesInCandidate = candidate.lineEnd - candidate.lineStart + 1
            val pc: Double = (linesInCandidate.toDouble() / linesInFunctionBody)
            val result = pc < threshold
            return result
        }
    }
}