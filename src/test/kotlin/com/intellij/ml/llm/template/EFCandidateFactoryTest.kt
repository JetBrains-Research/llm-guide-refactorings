package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EfCandidateType
import com.intellij.ml.llm.template.utils.CodeTransformer
import com.intellij.ml.llm.template.utils.EFCandidateFactory
import com.intellij.ml.llm.template.utils.isCandidateExtractable
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase

class EFCandidateFactoryTest : LightPlatformCodeInsightTestCase() {
    private var projectPath = "src/test"
    override fun getTestDataPath(): String {
        return projectPath
    }

    fun `test extract function candidates equality`() {
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 10,
            lineEnd = 20
        )
        val efc1 = EFCandidate(
            functionName = "foo",
            lineStart = 10,
            lineEnd = 20,
            offsetStart = 100,
            offsetEnd = 200,
        ).also {
            it.efSuggestion = efs
            it.type = EfCandidateType.AS_IS
        }
        val efc2 = EFCandidate(
            functionName = "foo",
            lineStart = 10,
            lineEnd = 20,
            offsetStart = 100,
            offsetEnd = 200,
        ).also {
            it.efSuggestion = efs
            it.type = EfCandidateType.ADJUSTED
        }

        TestCase.assertTrue(efc1.equals(efc2))
    }

    fun `test build invalid candidate from invalid suggestion`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efSuggestion = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 0,
            lineEnd = -1
        )

        val efCandidates = EFCandidateFactory().buildCandidates(efSuggestion, editor, file).toTypedArray()
        TestCase.assertEquals(1, efCandidates.size)
        TestCase.assertEquals(0, efCandidates.get(0).lineStart)
        TestCase.assertEquals(0, efCandidates.get(0).lineEnd)
        TestCase.assertEquals(-1, efCandidates.get(0).offsetStart)
        TestCase.assertEquals(-1, efCandidates.get(0).offsetEnd)
        TestCase.assertEquals(EfCandidateType.INVALID, efCandidates.get(0).type)
    }

    fun `test extract function candidate is the same as the extract function suggestion`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")

        val efSuggestion = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 50,
            lineEnd = 64
        )
        val candidateFactory = EFCandidateFactory()
        val efCandidates = candidateFactory.buildCandidates(efSuggestion, editor, file).toTypedArray()

        TestCase.assertEquals(1, efCandidates.size)
        TestCase.assertEquals(efSuggestion.functionName, efCandidates.get(0).functionName)
        TestCase.assertEquals(efSuggestion.lineStart, efCandidates.get(0).lineStart)
        TestCase.assertEquals(efSuggestion.lineEnd, efCandidates.get(0).lineEnd)
        TestCase.assertEquals(efSuggestion, efCandidates.get(0).efSuggestion)
        TestCase.assertEquals(3589, efCandidates.get(0).offsetStart)
        TestCase.assertEquals(4940, efCandidates.get(0).offsetEnd)
        TestCase.assertEquals(EfCandidateType.AS_IS, efCandidates.get(0).type)
    }

    fun `test filter out extract function candidates that don't work`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs1 = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 50,
            lineEnd = 64,
        )
        val efs2 = EFSuggestion(
            functionName = "fooBar",
            lineStart = 1,
            lineEnd = 1
        )
        val candidateFactory = EFCandidateFactory()
        val efCandidates = ArrayList<EFCandidate>()
        efCandidates.addAll(candidateFactory.buildCandidates(efs1, editor, file))
        efCandidates.addAll(candidateFactory.buildCandidates(efs2, editor, file))

        TestCase.assertEquals(2, efCandidates.size)

        val codeTransformer = CodeTransformer()
        val workingEFCandidates = ArrayList<EFCandidate>()
        efCandidates.forEach { candidate ->
            configureByFile("/testdata/KafkaAdminClientTest.java")
            if (codeTransformer.applyCandidate(candidate, project, editor, file)) {
                workingEFCandidates.add(candidate)
            }
        }

        TestCase.assertEquals(1, workingEFCandidates.size)
    }

    fun `test generate two candidates for one suggestion`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 113,
            lineEnd = 119
        )

        val expectedCandidate1 = EFCandidate(
            functionName = efs.functionName,
            lineStart = 113,
            lineEnd = 119,
            offsetStart = 7628,
            offsetEnd = 8061,
        ).also {
            it.efSuggestion = efs
            it.type = EfCandidateType.AS_IS
        }

        val expectedCandidate2 = EFCandidate(
            functionName = efs.functionName,
            lineStart = 113,
            lineEnd = 120,
            offsetStart = 7628,
            offsetEnd = 8075
        ).also {
            it.efSuggestion = efs
            it.type = EfCandidateType.ADJUSTED
        }
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()

        TestCase.assertEquals(2, candidates.size)

        arrayOf(expectedCandidate1, expectedCandidate2).forEach { expectedCandidate ->
            TestCase.assertTrue(candidates.contains(expectedCandidate))
            TestCase.assertEquals(expectedCandidate.type, candidates.get(candidates.indexOf(expectedCandidate)).type)
        }
    }

    fun `test candidate is extractable in java code`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 113,
            lineEnd = 120
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
        TestCase.assertEquals(1, candidates.size)
        TestCase.assertTrue(isCandidateExtractable(candidates.get(0), editor, file))
    }

    fun `test candidate is not extractable in Java code`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 113,
            lineEnd = 119
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.AS_IS }
        TestCase.assertEquals(1, candidates.size)
        TestCase.assertFalse(isCandidateExtractable(candidates.get(0), editor, file))
    }

    fun `test multiple assignment selection in Java code`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        com.intellij.openapi.util.registry.Registry.get("refactorings.extract.method.introduce.object").setValue(false)
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 18,
            lineEnd = 22
        )

        var candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
        TestCase.assertEquals(1, candidates.size)
        candidates = candidates.filter { it.type == EfCandidateType.AS_IS }.toTypedArray()
        TestCase.assertEquals(1, candidates.size)
        TestCase.assertFalse(isCandidateExtractable(candidates.get(0), editor, file))
    }

    fun `test entire function is not extractable in Java code`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        com.intellij.openapi.util.registry.Registry.get("refactorings.extract.method.introduce.object").setValue(true)
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 5,
            lineEnd = 122
        )

        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.AS_IS && isCandidateExtractable(it, editor, file) }
        TestCase.assertEquals(0, candidates.size)
    }

    fun `test candidate is extractable in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 12,
            lineEnd = 17
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.AS_IS && isCandidateExtractable(it, editor, file) }
        TestCase.assertEquals(1, candidates.size)
    }

    fun `test candidate is not extractable in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 12,
            lineEnd = 16
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.AS_IS && isCandidateExtractable(it, editor, file) }
        TestCase.assertEquals(0, candidates.size)
    }

    fun `test entire function is not extractable in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "createPartitionMetadata",
            lineStart = 8,
            lineEnd = 18
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.AS_IS && isCandidateExtractable(it, editor, file) }
        println(editor.selectionModel.selectedText)
        TestCase.assertEquals(0, candidates.size)
    }
}