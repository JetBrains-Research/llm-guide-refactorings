package evaluation

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.utils.EFCandidateFactory
import com.intellij.ml.llm.template.utils.PsiUtils
import com.intellij.ml.llm.template.utils.isCandidateExtractable
import com.intellij.ml.llm.template.utils.CodeMetricsUtils
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase

class CodeMetricsUtilsTest: LightPlatformCodeInsightTestCase() {
    private var projectPath = "src/test"

    override fun getTestDataPath(): String {
        return projectPath
    }

    fun `test cyclomatic complexity computed correctly on Java code 1`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val startOffset = 9986
        val endOffset = 10149
        val expectedCCN = 0
        val cm = CodeMetricsUtils()

        TestCase.assertEquals(expectedCCN, cm.calculateCyclomaticComplexity(file, startOffset, endOffset))
    }

    fun `test cyclomatic complexity computed correctly on Java code 2`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val startOffset = 7828
        val endOffset = 7887
        val expectedCCN = 2
        val cm = CodeMetricsUtils()

        TestCase.assertEquals(expectedCCN, cm.calculateCyclomaticComplexity(file, startOffset, endOffset))
    }

    fun `test cyclomatic complexity computed correctly on Java code 3`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val startOffset = 7913
        val endOffset = 8299
        val expectedCCN = 5
        val cm = CodeMetricsUtils()

        TestCase.assertEquals(expectedCCN, cm.calculateCyclomaticComplexity(file, startOffset, endOffset))
    }

    fun `test cyclomatic complexity computed correctly on Java code 4`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val startOffset = 6910
        val endOffset = 8444
        val expectedCCN = 13
        val cm = CodeMetricsUtils()

        TestCase.assertEquals(expectedCCN, cm.calculateCyclomaticComplexity(file, startOffset, endOffset))
    }

    fun `test cyclomatic complexity computed correctly on Java code 5`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val startOffset = 10217
        val endOffset = 10375
        val expectedCCN = 6
        val cm = CodeMetricsUtils()

        TestCase.assertEquals(expectedCCN, cm.calculateCyclomaticComplexity(file, startOffset, endOffset))
    }

    fun `test cyclomatic complexity computed correctly on Java code 6`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val startOffset = 10553
        val endOffset = 11110
        val expectedCCN = 3
        val cm = CodeMetricsUtils()

        TestCase.assertEquals(expectedCCN, cm.calculateCyclomaticComplexity(file, startOffset, endOffset))
    }

    fun `test nesting depth of a method on Java code 1`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val expectedNestingDepth = 6
        val (startOffset, endOffset) = 10198 to 10375
        val elements = PsiUtils.findAllStatementsInRange(startOffset, endOffset, file)
        TestCase.assertEquals(expectedNestingDepth, CodeMetricsUtils().calculateRecursiveNestingDepth(elements))
    }

    fun `test nesting depth of a method on Java code 2`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val expectedNestingDepth = 2
        val (startOffset, endOffset) = 9245 to 9939
        val elements = PsiUtils.findAllStatementsInRange(startOffset, endOffset, file)
        TestCase.assertEquals(expectedNestingDepth, CodeMetricsUtils().calculateRecursiveNestingDepth(elements))
    }

    fun `test nesting depth of a code region in Java code 3`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val expectedNestingDepth = 18
        val (startOffset, endOffset) = 7913 to 8299
        val elements = PsiUtils.getStatementsBetweenOffsets(startOffset, endOffset, file)
        TestCase.assertEquals(expectedNestingDepth, CodeMetricsUtils().calculateRecursiveNestingDepth(elements))
    }

    fun `test calculate nesting depth for an element 1`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val cm = CodeMetricsUtils()
        val offset = 9986
        val expectedNestingDepth = 2
        val element = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiStatement::class.java, false)

        TestCase.assertEquals(expectedNestingDepth, cm.calculateNestingDepth(element!!))
    }

    fun `test calculate nesting depth for an element 2`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val cm = CodeMetricsUtils()
        val offset = 10002
        val expectedNestingDepth = 2
        val element = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiStatement::class.java, false)

        TestCase.assertEquals(expectedNestingDepth, cm.calculateNestingDepth(element!!))
    }

    fun `test calculate nesting area 1`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val cm = CodeMetricsUtils()
        val (startOffset, endOffset) = 10198 to 10227
//        val elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset).asList()
        val elements = PsiUtils.getStatementsBetweenOffsets(startOffset, endOffset, file)
        val expectedNestingArea = 4

        TestCase.assertEquals(expectedNestingArea, cm.calculateNestingArea(elements))
    }

    fun `test calculate nesting area 2`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val cm = CodeMetricsUtils()
        val (startOffset, endOffset) = 10443 to 10507
        val elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset).asList()
        val expectedNestingArea = 12

        TestCase.assertEquals(expectedNestingArea, cm.calculateNestingArea(elements))
    }

    fun `test calculate length score`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val efSuggestion = EFSuggestion(
            functionName = "testExtracted",
            lineStart = 203,
            lineEnd = 215
        )
        val efCandidates = EFCandidateFactory().buildCandidates(efSuggestion, editor, file).filter { isCandidateExtractable(it, editor, file, emptyList()) }
        val expectedLengthScore = 0.30000000000000004
        val actualLengthScore = CodeMetricsUtils().calculateScoreLength(efCandidates[0], file)

        TestCase.assertEquals(expectedLengthScore, actualLengthScore)
    }

    fun `test calculate nesting depth score`() {
        configureByFile("/testdata/ExtractMethodHelper.java")
        val efSuggestion = EFSuggestion(
            functionName = "testExtracted",
            lineStart = 203,
            lineEnd = 215
        )
        val efCandidates = EFCandidateFactory().buildCandidates(efSuggestion, editor, file).filter { isCandidateExtractable(it, editor, file, emptyList()) }
        val expectedNestingDepthScore = 0.30000000000000004
        val actualNestingDepthScore = CodeMetricsUtils().calculateNestingDepthScore(efCandidates[0], file)
    }
}