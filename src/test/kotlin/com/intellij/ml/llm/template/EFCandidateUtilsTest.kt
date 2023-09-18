package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.evaluation.HostFunctionData
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.utils.EFCandidateUtils
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

class EFCandidateUtilsTest : LightPlatformTestCase() {
    fun `test rank by popularity works correctly`() {
        val candidates = listOf(
            EFCandidate("foo", 2868, 3505, 66, 75),
            EFCandidate("foo", 2948, 3505, 67, 75),
            EFCandidate("foo", 2948, 3505, 67, 75),
        )
        val ranked = EFCandidateUtils.rankByPopularity(candidates)
        TestCase.assertEquals(2, ranked.size)
        TestCase.assertEquals(67, ranked.first().lineStart)
        TestCase.assertEquals(75, ranked.first().lineEnd)
        TestCase.assertEquals(66, ranked.last().lineStart)
        TestCase.assertEquals(75, ranked.last().lineEnd)
    }

    fun `test rank by heat works correctly`() {
        val hostFunctionData = HostFunctionData(
            lineStart = 10,
            lineEnd = 60,
            bodyLoc = 48,
            githubUrl = "foo"
        )
        val c1 = EFCandidate("foo", 2868, 3505, 12, 20)
        val c2 = EFCandidate("foo", 2948, 3505, 15, 25)
        val c3 = EFCandidate("foo", 2948, 3505, 17, 40)
        val c4 = EFCandidate("foo", 2948, 3505, 50, 55)
        val candidates = listOf(c1, c2, c3, c4)
        val expectedRanking = listOf(c3, c2, c1, c4)

        val ranked = EFCandidateUtils.rankByHeat(candidates, hostFunctionData)

        TestCase.assertEquals(19, c1.heat)
        TestCase.assertEquals(26, c2.heat)
        TestCase.assertEquals(37, c3.heat)
        TestCase.assertEquals(6, c4.heat)
        TestCase.assertEquals(expectedRanking, ranked)
    }

    fun `test sort by overlap and popularity`() {
        val c1 = EFCandidate("foo", 935, 3588, 32, 116)
        val c2 = EFCandidate("foo", 1323, 3584, 49, 115)
        val c3 = EFCandidate("foo", 1425, 2438, 51, 80)
        val c4 = EFCandidate("foo", 737, 932, 26, 31)
        val c5 = EFCandidate("foo", 737, 932, 27, 31)
        val c6 = EFCandidate("foo", 795, 932, 27, 31)
        val candidates = listOf(c1, c2, c3, c4, c5, c6)
        val rankedCandidates = EFCandidateUtils.rankByOverlap(candidates)
        val expectedRanked = listOf(c5, c4, c3, c1, c2)

        TestCase.assertEquals(expectedRanked, rankedCandidates)
    }
}