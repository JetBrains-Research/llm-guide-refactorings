package com.intellij.ml.llm.template

import com.intellij.ml.llm.template.extractfunction.*
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

        TestCase.assertEquals(2, efCandidates.size)
        val adjustedCandidate = efCandidates.filter { it.type == EfCandidateType.ADJUSTED }.first()
        TestCase.assertEquals(efSuggestion.functionName, adjustedCandidate.functionName)
        TestCase.assertEquals(efSuggestion.lineStart-1, adjustedCandidate.lineStart)
        TestCase.assertEquals(efSuggestion.lineEnd, adjustedCandidate.lineEnd)
        TestCase.assertEquals(efSuggestion, adjustedCandidate.efSuggestion)
        TestCase.assertEquals(3513, adjustedCandidate.offsetStart)
        TestCase.assertEquals(4940, adjustedCandidate.offsetEnd)
        TestCase.assertEquals(EfCandidateType.ADJUSTED, adjustedCandidate.type)

        val asisCandidate = efCandidates.filter { it.type == EfCandidateType.AS_IS }.first()
        TestCase.assertEquals(efSuggestion.functionName, asisCandidate.functionName)
        TestCase.assertEquals(efSuggestion.lineStart, asisCandidate.lineStart)
        TestCase.assertEquals(efSuggestion.lineEnd, asisCandidate.lineEnd)
        TestCase.assertEquals(efSuggestion, asisCandidate.efSuggestion)
        TestCase.assertEquals(3589, asisCandidate.offsetStart)
        TestCase.assertEquals(4940, asisCandidate.offsetEnd)
        TestCase.assertEquals(EfCandidateType.AS_IS, asisCandidate.type)
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

        TestCase.assertEquals(2, workingEFCandidates.size)
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
        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toTypedArray()
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

        var candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toTypedArray()
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
            TestCase.assertEquals(
                LLMBundle.message("extract.function.entire.function.selection.message"),
                (it.payload as EFCandidateApplicationPayload).reason
            )
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

        val successPayload =
            efObserver.getNotifications(EFApplicationResult.OK).get(0).payload as EFCandidateApplicationPayload
        val failPayload =
            efObserver.getNotifications(EFApplicationResult.FAIL).get(0).payload as EFCandidateApplicationPayload

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
                lineEnd = 144
            )
        )
        val efCandidateFactory = EFCandidateFactory()
        val candidates = efCandidateFactory.buildDistinctCandidates(efSuggestions, editor, file).toTypedArray()
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
        val candidates = efCandidateFactory.buildDistinctCandidates(efSuggestions, editor, file).toTypedArray()
        val filteredCandidates = candidates.filter {
            isCandidateExtractable(it, editor, file, listOf(efObserver))
        }

        TestCase.assertTrue(filteredCandidates.isEmpty())
        TestCase.assertEquals(8, efObserver.getNotifications().size)
        efObserver.getNotifications().forEach {
            TestCase.assertEquals(
                LLMBundle.message("extract.function.entire.function.selection.message"),
                (it.payload as EFCandidateApplicationPayload).reason
            )
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
        TestCase.assertEquals(26602, adjustedCandidates.get(0).offsetStart)
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

    /**
     * When a suggestion startLine and endLine belong to two different functions,
     * no adjusted candidate should be generated
     */
    fun `test cross functions suggestion in Kotlin code`() {
        configureByFile("/testdata/RodCuttingProblem.kt")
        val suggestions = listOf(
            EFSuggestion(
                functionName = "foo",
                lineStart = 14, // function 1
                lineEnd = 24    // function 2
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 20, // file
                lineEnd = 27    // file
            )
        )

        val candidates = EFCandidateFactory().buildCandidates(suggestions, editor, file).toTypedArray()
        TestCase.assertEquals(suggestions.size, candidates.size)
        candidates.forEach { TestCase.assertEquals(EfCandidateType.AS_IS, it.type) }
    }

    /**
     * When a suggestion startLine and endLine belong to two different functions,
     * no adjusted candidate should be generated
     */
    fun `test cross functions suggestion in Java code`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val suggestions = listOf(
            EFSuggestion(
                functionName = "foo",
                lineStart = 140,    // function 1
                lineEnd = 146       // function 2
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 134,    // class
                lineEnd = 139       // function
            ),
            EFSuggestion(
                functionName = "foo",
                lineStart = 123,    // class
                lineEnd = 149       // class
            )
        )

        val candidates = EFCandidateFactory().buildCandidates(suggestions, editor, file).toTypedArray()
        TestCase.assertEquals(suggestions.size, candidates.size)
        candidates.forEach { TestCase.assertEquals(EfCandidateType.AS_IS, it.type) }
    }

    /**
     * Test when lineStart/lineEnd falls in a middle of parameter list of a function call
     */
    fun `test suggestion within parameter list of function call Kotlin`() {
        configureByFile("/testdata/ReflektComponentRegistrar.kt")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 137,
            lineEnd = 142
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
        val adjustedCandidates = candidates.filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(6795, adjustedCandidates[0].offsetStart)
        TestCase.assertEquals(7093, adjustedCandidates[0].offsetEnd)
    }

    /**
     * Test when lineStart/lineEnd falls in a middle of parameter list of a function call
     */
    fun `test suggestion within parameter list of function call Java`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 31,
            lineEnd = 46
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
        val adjustedCandidates = candidates.filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(1709, adjustedCandidates[0].offsetStart)
        TestCase.assertEquals(3427, adjustedCandidates[0].offsetEnd)
    }

    /**
     * Test when lineStart/lineEnd falls in a middle of parameter list of a function declaration
     * The adjusted region should contain the entire function
     */
    fun `test suggestion within parameter list of a function declaration Kotlin`() {
        configureByFile("/testdata/ReflektComponentRegistrar.kt")
        var efs = EFSuggestion(
            functionName = "foo",
            lineStart = 122,
            lineEnd = 129
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
        val adjustedCandidates = candidates.filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(6153, adjustedCandidates[0].offsetStart)
        TestCase.assertEquals(7099, adjustedCandidates[0].offsetEnd)
    }

    /**
     * Test when lineStart/lineEnd falls in a middle of parameter list of a function declaration
     * The adjusted region should contain the entire function
     */
    fun `test suggestion within parameter list of a function declaration Java`() {
        configureByFile("/testdata/KafkaAdminClientTest.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 152,
            lineEnd = 155
        )
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toTypedArray()
        val adjustedCandidates = candidates.filter { it.type == EfCandidateType.ADJUSTED }
        TestCase.assertEquals(1, adjustedCandidates.size)
        TestCase.assertEquals(8465, adjustedCandidates[0].offsetStart)
        TestCase.assertEquals(8576, adjustedCandidates[0].offsetEnd)
    }

    /**
     * Test heuristic to prefer IF statement block as opposed to entire If statement
     */
    fun `test if block heuristic Java`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 43,
            lineEnd = 46
        )
        EFSettings.instance.addHeuristic(EMHeuristic.IF_BODY)
        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        val adjustedCandidate = candidates.filter { it.type == EfCandidateType.ADJUSTED }.first()
        TestCase.assertEquals(44, adjustedCandidate.lineStart)
        TestCase.assertEquals(45, adjustedCandidate.lineEnd)
    }

    fun `test if block heuristic Java 2`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 42,
            lineEnd = 46
        )
        EFSettings.instance.addHeuristic(EMHeuristic.IF_BODY)
        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toList()
        TestCase.assertEquals(1, candidates.size)
        val filteredCandidate = candidates.filter { it.type == EfCandidateType.AS_IS }.first()
        TestCase.assertEquals(42, filteredCandidate.lineStart)
        TestCase.assertEquals(46, filteredCandidate.lineEnd)
    }

    /**
     * Test heuristic to include the statement before the selected bloc if it is a write
     * to a variable that is used inside the block
     */
    fun `test previous statement heuristic Java`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 7,
            lineEnd = 10
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        val targetCandidate = candidates.filter { it.type == EfCandidateType.ADJUSTED }.first()
        TestCase.assertEquals(6, targetCandidate.lineStart)
        TestCase.assertEquals(10, targetCandidate.lineEnd)
    }

    fun `test previous statement heuristic Java 2`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 19,
            lineEnd = 29
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        val targetCandidate = candidates.filter { it.type == EfCandidateType.ADJUSTED }.first()
        TestCase.assertEquals(18, targetCandidate.lineStart)
        TestCase.assertEquals(29, targetCandidate.lineEnd)
    }

    fun `test previous statement heuristic Java 3`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 37,
            lineEnd = 39
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        val targetCandidate = candidates.filter { it.type == EfCandidateType.ADJUSTED }.first()
        TestCase.assertEquals(36, targetCandidate.lineStart)
        TestCase.assertEquals(39, targetCandidate.lineEnd)
    }

    fun `test previous statement heuristic Java 4`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 51,
            lineEnd = 59
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        val targetCandidate = candidates.filter { it.type == EfCandidateType.ADJUSTED }.first()
        TestCase.assertEquals(50, targetCandidate.lineStart)
        TestCase.assertEquals(59, targetCandidate.lineEnd)
    }

    fun `test previous statement heuristic Java 5`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 74,
            lineEnd = 94
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toList()
        TestCase.assertEquals(1, candidates.size)
        TestCase.assertEquals(EfCandidateType.AS_IS, candidates.first().type)
    }

    fun `test previous statement heuristic Java 6`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 119,
            lineEnd = 122
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        TestCase.assertEquals(EfCandidateType.AS_IS, candidates.first().type)
        TestCase.assertEquals(EfCandidateType.ADJUSTED, candidates.last().type)
    }

    fun `test previous statement heuristic Java 7`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 134,
            lineEnd = 139
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        TestCase.assertEquals(EfCandidateType.AS_IS, candidates.first().type)
        TestCase.assertEquals(EfCandidateType.ADJUSTED, candidates.last().type)
    }

    fun `test previous statement heuristic Java 8`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 170,
            lineEnd = 184
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)

        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
        TestCase.assertEquals(EfCandidateType.AS_IS, candidates.first().type)
        TestCase.assertEquals(EfCandidateType.ADJUSTED, candidates.last().type)
    }

    fun `test heuristic`() {
        configureByFile("/testdata/Heuristics.java")
        val efs = EFSuggestion(
            functionName = "foo",
            lineStart = 217,
            lineEnd = 223
        )
        EFSettings.instance.addHeuristic(EMHeuristic.PREV_ASSIGNMENT)
        EFSettings.instance.addHeuristic(EMHeuristic.IF_BODY)

        val candidates = EFCandidateFactory().buildDistinctCandidates(efs, editor, file).toList()
        TestCase.assertEquals(2, candidates.size)
    }

    fun `test above 90% lines candidates are discarded`() {
        configureByFile("/testdata/Heuristics.java")
        val efs1 = EFSuggestion(
            functionName = "foo",
            lineStart = 157,
            lineEnd = 188
        )
        val efs2 = EFSuggestion(
            functionName = "foo2",
            lineStart = 157,
            lineEnd = 158
        )

        EFSettings.instance.addHeuristic(EMHeuristic.MAX_METHOD_LOC_THRESHOLD)

        val candidates = EFCandidateFactory().buildDistinctCandidates(listOf(efs1, efs2), editor, file).toList()
        TestCase.assertEquals(1, candidates.size)
    }
}