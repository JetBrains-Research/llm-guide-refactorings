package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EfCandidateType
import com.intellij.ml.llm.template.utils.CodeTransformer
import com.intellij.ml.llm.template.utils.EFApplicationResult
import com.intellij.ml.llm.template.utils.EFCandidateFactory
import com.intellij.ml.llm.template.utils.EFObserver
import com.intellij.openapi.util.Key
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.TestModeFlags
import junit.framework.TestCase

class CodeTransformerTest : LightPlatformCodeInsightTestCase() {
    private var projectPath = "src/test"
    private val ourTemplateTesting = Key.create<Boolean>("TemplateTesting")
    override fun getTestDataPath(): String {
        return projectPath
    }

    fun `test failed extract function candidates are reported correctly`() {
        val codeTransformer = CodeTransformer()
        val efObserver = EFObserver()
        codeTransformer.addObserver(efObserver)

        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 113,
            lineEnd = 119
        )
        val efCandidates = EFCandidateFactory().buildCandidates(efs, editor, file)
        efCandidates.forEach { candidate ->
            configureByFile("/testdata/KafkaAdminClientTest.java")
            codeTransformer.applyCandidate(candidate, project, editor, file)
        }

        val failedNotifications = efObserver.getNotifications(EFApplicationResult.FAIL)
        val successNotifications = efObserver.getNotifications(EFApplicationResult.OK)
        TestCase.assertEquals(1, failedNotifications.size)
        TestCase.assertEquals(1, successNotifications.size)
    }

    fun `test kotlin code extract function with adjusted code region success`() {
        TestModeFlags.set(ourTemplateTesting, true)
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "computeValues",
            lineStart = 12,
            lineEnd = 16
        )
        val codeTransformer = CodeTransformer()
        val efCandidates = EFCandidateFactory().buildCandidates(efs, editor, file)
        efCandidates.filter { it.type == EfCandidateType.ADJUSTED }.take(1).forEach { candidate ->
            configureByFile("/testdata/RodCuttingProblem.kt")
            TestCase.assertTrue(codeTransformer.applyCandidate(candidate, project, editor, file))
            checkResultByFile("/testdata/RodCuttingProblem_ef1.kt")
        }
    }

    fun `test kotlin code extract function with default code region success`() {
        TestModeFlags.set(ourTemplateTesting, true)
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "computeValues",
            lineStart = 12,
            lineEnd = 17
        )
        val codeTransformer = CodeTransformer()
        val efCandidates = EFCandidateFactory().buildCandidates(efs, editor, file)
        TestCase.assertEquals(1, efCandidates.size)
        efCandidates.filter { it.type == EfCandidateType.AS_IS }.take(1).forEach { candidate ->
            configureByFile("/testdata/RodCuttingProblem.kt")
            TestCase.assertTrue(codeTransformer.applyCandidate(candidate, project, editor, file))
            checkResultByFile("/testdata/RodCuttingProblem_ef1.kt")
        }
    }

    fun `test kotlin code extract function with default code region fail`() {
        TestModeFlags.set(ourTemplateTesting, true)
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "computeValues",
            lineStart = 12,
            lineEnd = 16
        )
        val codeTransformer = CodeTransformer()
        val efCandidates = EFCandidateFactory().buildCandidates(efs, editor, file)
        TestCase.assertEquals(2, efCandidates.size)
        efCandidates.filter { it.type == EfCandidateType.AS_IS }.take(1).forEach { candidate ->
            configureByFile("/testdata/RodCuttingProblem.kt")
            TestCase.assertFalse(codeTransformer.applyCandidate(candidate, project, editor, file))
        }
    }
}