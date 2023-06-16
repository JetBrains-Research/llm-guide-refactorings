package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EfCandidateType
import com.intellij.ml.llm.template.utils.*
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

        TestCase.assertEquals(3, efCandidates.size)

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


    /**
     * The following suggestions should all result in candidates
     * that should fail because they select the entire function to be extracted
     */
    fun `test entire function body is not extractable in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efSuggestions = listOf(
            EFSuggestion(
                functionName = "createPartitionMetadata",
                lineStart = 9,
                lineEnd = 18
            ),
            EFSuggestion(
                functionName = "createPartitionMetadata",
                lineStart = 9,
                lineEnd = 19
            ),
            EFSuggestion(
                functionName = "createPartitionMetadata",
                lineStart = 22,
                lineEnd = 26
            ),
            EFSuggestion(
                functionName = "createPartitionMetadata",
                lineStart = 22,
                lineEnd = 25
            ),
            EFSuggestion(
                functionName = "createPartitionMetadata",
                lineStart = 29,
                lineEnd = 31
            ),
            EFSuggestion(
                functionName = "createPartitionMetadata",
                lineStart = 34,
                lineEnd = 36
            ),
        )
        val efObserver = EFObserver()
        val filteredCandidates =
            EFCandidateFactory().buildCandidates(efSuggestions, editor, file)
                .toTypedArray()
                .filter {
                    isCandidateExtractable(it, editor, file, listOf(efObserver))
                }
        TestCase.assertTrue(filteredCandidates.isEmpty())
        efObserver.getNotifications().forEach {
            TestCase.assertEquals(LLMBundle.message("extract.function.entire.function.selection.message"), (it.payload as EFCandidateApplicationPayload).reason)
        }
    }

    fun `test extractable function in various corner cases in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efSuggestions = listOf(
            EFSuggestion(
                functionName = "foo",
                lineStart = 23,
                lineEnd = 24
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 30,
                lineEnd = 31
            )
        )
        val efObserver = EFObserver()
        val candidates = EFCandidateFactory().buildCandidates(efSuggestions, editor, file).toTypedArray()
        val filteredCandidates = candidates.filter {
            isCandidateExtractable(it, editor, file, listOf(efObserver))
        }
        TestCase.assertEquals(candidates.size, filteredCandidates.size)
        TestCase.assertEquals(filteredCandidates.size, efObserver.getNotifications().size)
        efObserver.getNotifications().forEach {
            TestCase.assertEquals(EFApplicationResult.OK, (it.payload as EFCandidateApplicationPayload).result)
        }
    }


    /**
     * In this case, there should be two candidates:
     * 1. The AS_IS candidate should fail
     * 2. The ADJUSTED candidate should succeed because it selects the first statement after the function's bracket
     */
    fun `test first line contains the function's open bracket`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 34,
            lineEnd = 35
        )
        val efObserver = EFObserver()
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()

        candidates.forEach {
            isCandidateExtractable(it, editor, file, listOf(efObserver))
        }

        TestCase.assertEquals(2, candidates.size)
        TestCase.assertEquals(1, efObserver.getNotifications(EFApplicationResult.OK).size)
        TestCase.assertEquals(1, efObserver.getNotifications(EFApplicationResult.FAIL).size)

        val successPayload = efObserver.getNotifications(EFApplicationResult.OK).get(0).payload as EFCandidateApplicationPayload
        val failPayload = efObserver.getNotifications(EFApplicationResult.FAIL).get(0).payload as EFCandidateApplicationPayload

        TestCase.assertEquals(
            EfCandidateType.ADJUSTED,
            successPayload.candidate.type
        )
        TestCase.assertEquals(
            EfCandidateType.AS_IS,
            failPayload.candidate.type
        )
    }

    /**
     * With this test we are testing that even though, we have two suggestions,
     * we only generate three distinct candidates, and not four. This is because
     * the adjusted region is the same for both suggestions.
     */
    fun `test two suggestions generate two as-is and one adjusted candidates`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val efSuggestions = listOf(
            EFSuggestion(
                functionName = "foo",
                lineStart = 120,
                lineEnd = 130
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 135,
                lineEnd = 145
            )
        )
        val efCandidateFactory = EFCandidateFactory()
        val candidates = efCandidateFactory.buildCandidates(efSuggestions, editor, file).toTypedArray()
        TestCase.assertEquals(3, candidates.size)
        TestCase.assertEquals(2, candidates.filter { it.type == EfCandidateType.AS_IS }.size)
        TestCase.assertEquals(1, candidates.filter { it.type == EfCandidateType.ADJUSTED }.size)
    }


    /**
     * The following suggestions should all result in candidates
     * that should fail because they select the entire function to be extracted
     */
    fun `test entire function body is not extractable in Java code`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efSuggestions = listOf(
            EFSuggestion(
                functionName = "foo",
                lineStart = 124,
                lineEnd = 127
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 125,
                lineEnd = 126
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 130,
                lineEnd = 133
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 130,
                lineEnd = 132
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 131,
                lineEnd = 133
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 137,
                lineEnd = 141
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 145,
                lineEnd = 148
            )
        )
        val efCandidateFactory = EFCandidateFactory()
        val efObserver = EFObserver()
        val candidates = efCandidateFactory.buildCandidates(efSuggestions, editor, file).toTypedArray()
        val filteredCandidates = candidates.filter {
            isCandidateExtractable(it, editor, file, listOf(efObserver))
        }

        TestCase.assertTrue(filteredCandidates.isEmpty())
        TestCase.assertEquals(8, efObserver.getNotifications().size)
        efObserver.getNotifications().forEach {
            TestCase.assertEquals(LLMBundle.message("extract.function.entire.function.selection.message"), (it.payload as EFCandidateApplicationPayload).reason)
        }
    }


    /**
     * The purpose of this is to test various situations in which either the beginning of the code region,
     * or the end need to be "bubbled up" to be at the same level
     * Java code
     */
    fun `test bubble up code selection in Java code`() {
        configureByFile("/testdata/CommandLineLexer.java")
        var efs = EFSuggestion(
            functionName = "foo",
            lineStart = 692,
            lineEnd = 703
        )
        var adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(27130, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(28942, adjustedCandidates.get(0).offsetEnd)

        efs = EFSuggestion(
            functionName = "foo",
            lineStart = 692,
            lineEnd = 699
        )
        adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(28465, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(28923, adjustedCandidates.get(0).offsetEnd)

        efs = EFSuggestion(
            functionName = "foo",
            lineStart = 692,
            lineEnd = 707
        )
        adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(27099, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(29039, adjustedCandidates.get(0).offsetEnd)

        efs = EFSuggestion(
            functionName = "foo",
            lineStart = 644,
            lineEnd = 647
        )
        adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(26640, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(31913, adjustedCandidates.get(0).offsetEnd)
    }

    /**
     * The purpose of this is to test various situations in which either the beginning of the code region,
     * or the end need to be "bubbled up" to be at the same level
     * Kotlin code
     */
    fun `test bubble up code selection in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        var efs = EFSuggestion(
            functionName = "foo",
            lineStart = 10,
            lineEnd = 14
        )
        var adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(321, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(523, adjustedCandidates.get(0).offsetEnd)

        efs = EFSuggestion(
            functionName = "foo",
            lineStart = 14,
            lineEnd = 18
        )
        adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(339, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(552, adjustedCandidates.get(0).offsetEnd)

        efs = EFSuggestion(
            functionName = "foo",
            lineStart = 16,
            lineEnd = 18
        )
        adjustedCandidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
            .filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(339, adjustedCandidates.get(0).offsetStart)
        TestCase.assertEquals(552, adjustedCandidates.get(0).offsetEnd)
    }
}