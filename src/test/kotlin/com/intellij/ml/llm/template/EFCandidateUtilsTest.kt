package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.utils.EFCandidateUtils
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase

class EFCandidateUtilsTest: LightPlatformTestCase() {
    fun `test rank by heat is works correctly`() {
        val candidates = listOf(
            EFCandidate("foo", 2868, 3505, 66, 75),
            EFCandidate("foo", 2948, 3505, 67, 75),
            EFCandidate("foo", 2948, 3505, 67, 75),
        )
        val ranked = EFCandidateUtils.rankByHeat(candidates)
        TestCase.assertEquals(2, ranked.size)
        TestCase.assertEquals(66, ranked.first().lineStart)
        TestCase.assertEquals(75, ranked.first().lineEnd)
        TestCase.assertEquals(67, ranked.last().lineStart)
        TestCase.assertEquals(75, ranked.last().lineEnd)
        TestCase.assertEquals(18, ranked.first().heat)
        TestCase.assertEquals(19, ranked.last().heat)
    }

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
}