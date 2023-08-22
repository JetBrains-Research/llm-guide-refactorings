package com.intellij.ml.llm.template.common

import com.intellij.ml.llm.template.common.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.common.extractfunction.MethodExtractionType
import com.intellij.ml.llm.template.common.utils.CodeTransformer
import com.intellij.ml.llm.template.common.utils.EFApplicationResult
import com.intellij.ml.llm.template.common.utils.EFCandidateFactory
import com.intellij.ml.llm.template.common.utils.EFObserver
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase

class CodeTransformerTest : LightPlatformCodeInsightTestCase() {
    private var projectPath = "src/test"
    override fun getTestDataPath(): String {
        return projectPath
    }

    fun `test failed extract function candidates are reported correctly`() {
        val codeTransformer = CodeTransformer(MethodExtractionType.PARENT_CLASS)
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
}