package com.intellij.ml.llm.template.utils

import com.intellij.ml.llm.template.extractfunction.EFCandidate

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

        fun computeHeat(candidates: List<EFCandidate>): List<EFCandidate> {
            candidates.map { it.heat = 0 }
            for (ci in candidates.indices) {
                val currentCandidate = candidates.get(ci)
                for (oi in candidates.indices) {
                    if (oi == ci) continue
                    val otherCandidate = candidates.get(oi)
                    val ciStart = currentCandidate.lineStart
                    val ciEnd = currentCandidate.lineEnd
                    val oiStart = otherCandidate.lineStart
                    val oiEnd = otherCandidate.lineEnd
                    val heat = maxOf(0, minOf(ciEnd, oiEnd) - maxOf(ciStart, oiStart) + 1)
                    currentCandidate.heat += heat
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

        fun rankByHeat(candidates: List<EFCandidate>): List<EFCandidate> {
            return computeHeat(candidates).distinctBy { listOf(it.lineStart, it.lineEnd) }.sortedByDescending { it.heat }
        }

        fun rankBySize(candidates: List<EFCandidate>): List<EFCandidate> {
            return candidates.distinctBy { listOf(it.lineStart, it.lineEnd) }.sortedByDescending { it.length }
        }
    }
}