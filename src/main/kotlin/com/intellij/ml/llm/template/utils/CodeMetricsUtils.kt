package com.intellij.ml.llm.template.utils

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*
import java.util.*
import kotlin.math.max
import kotlin.math.min

class CodeMetricsUtils {
    private fun calculateCyclomaticComplexity(elements: List<PsiElement>): Int {
        var complexity = 0 // Start with 1 for the entry point

        for (element in elements) {
            complexity += when (element) {
                // Kotlin
                is KtIfExpression, is KtWhenExpression, is KtLoopExpression -> 1
                is KtReturnExpression -> 0
//                is KtBlockExpression -> 1 // Assuming blocks introduce a new path
                is KtBinaryExpression -> 1 // Assuming binary expressions introduce a new path

                // Java
                is PsiIfStatement, is PsiLoopStatement -> 1
//                is PsiBlockStatement -> 1
                is PsiBinaryExpression -> 1
                is PsiSwitchStatement -> 1

                // Handle other control flow constructs as needed
                else -> 0
            }

            // Recursively visit child elements
            complexity += calculateCyclomaticComplexity(element.children.toList())
        }

        return complexity
    }

    private fun calculateCyclomaticComplexity2(elements: List<PsiElement>): Int {
        var complexity = 0 // Start with 1 for the entry point
        val queue: Queue<PsiElement> = LinkedList()
        elements.forEach { queue.offer(it) }

        while (queue.isNotEmpty()) {
            val element = queue.poll()

            // add children to the list
            element.children.toList().forEach { queue.offer(it) }

            // analyze current element
            if (element is PsiSwitchStatement) {
                for (statement in element.body!!.statements) {
                    if (statement.text.contains("case")) complexity++
                }
            } else if (element is PsiLoopStatement) complexity++
            else if (element is PsiBinaryExpression && element.operationTokenType.toString()
                    .trim() == "EQEQ"
            ) complexity++
            else if (element is PsiIfStatement) complexity++
            else if (element is PsiKeyword && element.text == "else") complexity++
        }
//        for (element in elements) {
//            complexity += when (element) {
//                // Kotlin
//                is KtIfExpression, is KtWhenExpression, is KtLoopExpression -> 1
//                is KtReturnExpression -> 0
////                is KtBlockExpression -> 1 // Assuming blocks introduce a new path
//                is KtBinaryExpression -> 1 // Assuming binary expressions introduce a new path
//                // Handle other control flow constructs as needed
//                else -> 0
//            }
//
//            // Recursively visit child elements
//            complexity += calculateCyclomaticComplexity(element.children.toList())
//        }

        return complexity
    }

    fun calculateCyclomaticComplexity(psiFile: PsiFile, startOffset: Int, endOffset: Int): Int {
        val elementList = CodeInsightUtil.findStatementsInRange(psiFile, startOffset, endOffset).asList()
        return calculateCyclomaticComplexity2(elementList)
    }

    fun calculateRecursiveNestingDepth(elements: List<PsiStatement>): Int {
        var nestingDepth = 0
        if (elements.isEmpty()) return nestingDepth

        val topLevelElement = PsiTreeUtil.getParentOfType(elements[0], PsiMethod::class.java)
        val queue: Queue<PsiElement> = LinkedList()
        elements.forEach { queue.offer(it) }
        while (queue.isNotEmpty()) {
            val element = queue.poll()
            PsiTreeUtil.findChildrenOfType(element, PsiStatement::class.java)?.forEach {
                if (!queue.contains(it)) {
                    queue.offer(it)
                }
            }
            val currentElementNestingDepth = PsiTreeUtil.getDepth(element, topLevelElement)
            nestingDepth = max(currentElementNestingDepth, nestingDepth)
        }
        return nestingDepth
    }

    fun calculateNestingDepth(element: PsiElement): Int {
        val statement = if (element is PsiStatement) {
            element
        } else {
            PsiTreeUtil.getParentOfType(element, PsiStatement::class.java)
        }
        val topLevelElement = PsiTreeUtil.getParentOfType(statement, PsiMethod::class.java)
        val nestingDepth = if (statement == null) 0 else PsiTreeUtil.getDepth(statement, topLevelElement)
        return nestingDepth
    }

    fun calculateNestingArea(elements: List<PsiElement>): Int {
        var nestingArea = 0
        val queue: Queue<PsiElement> = LinkedList()
        elements.forEach { queue.offer(it) }
        while (queue.isNotEmpty()) {
            val element = queue.poll()
            PsiTreeUtil.findChildrenOfType(element, PsiStatement::class.java).forEach {
                if (!queue.contains(it)) {
                    queue.offer(it)
                }
            }
            nestingArea += calculateNestingDepth(element)
        }
        return nestingArea
    }

    /**
     * Slength = min(cl * min(Lc, Lr), MAXScoreLength)
     * cl = 0.1
     * MAXScoreLength = 3
     * Lc - length of the candidate
     * Lr - length of the remainder
     */
    fun calculateScoreLength(efCandidate: EFCandidate, file: PsiFile): Double {
        val cl = 0.1
        val MAXScoreLength = 3.0
        val Lc = efCandidate.lineEnd - efCandidate.lineStart + 1
        val methodLength =
            PsiUtils.countLinesOfCode(PsiUtils.getParentFunctionOrNull(efCandidate.offsetStart, file) as PsiMethod)
        val Lr = methodLength - Lc
        val scoreLength = min(cl * min(Lc, Lr), MAXScoreLength)

        return scoreLength
    }

    /**
     * SnestDepth = min(Dm - Dr, Dm - Dc)
     * Dm - nesting depth of the original method
     * Dr - nesting depth of the remainder
     * Dc - nesting depth of the candidate
     */
    fun calculateNestingDepthScore(efCandidate: EFCandidate, file: PsiFile): Double {
        val psiMethod = PsiUtils.getParentFunctionOrNull(efCandidate.offsetStart, file) as PsiMethod ?: return 0.0
        val methodElements = PsiTreeUtil.findChildrenOfType(psiMethod.body, PsiStatement::class.java).toList()
        val candidateElements = PsiUtils.findAllStatementsInRange(efCandidate.offsetStart, efCandidate.offsetEnd, file)
        val intersectingElements: List<PsiStatement> = methodElements.intersect(candidateElements).toList()
        val remainderElements = methodElements.filter { !intersectingElements.contains(it) }

        val Dm = calculateRecursiveNestingDepth(methodElements).toDouble()
        val Dr = remainderElements.map { calculateNestingDepth(it) }.max()
        val Dc = candidateElements.map { calculateNestingDepth(it)}.max()
        return min(Dm - Dr, Dm - Dc)
    }
}