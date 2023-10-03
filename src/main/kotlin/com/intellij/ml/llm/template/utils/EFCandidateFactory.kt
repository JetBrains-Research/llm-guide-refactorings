package com.intellij.ml.llm.template.utils

import com.intellij.lang.Language
import com.intellij.ml.llm.template.extractfunction.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.base.psi.getLineCount
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression

class EFCandidateFactory {
    fun buildCandidates(efSuggestion: EFSuggestion, editor: Editor, file: PsiFile): List<EFCandidate> {
        val candidates = mutableListOf<EFCandidate>()

        if (!isValid(efSuggestion, file)) {
            buildInvalidCandidate(efSuggestion).let { candidates.add(it) }
            return candidates
        }

        val asIsCandidate = buildCandidateAsIs(efSuggestion, editor, file)
        val adjustedCandidate = buildCandidateWithAdjustment(efSuggestion, editor, file)

        if (EFSettings.instance.hasHeuristic(EMHeuristic.KEEP_ADJUSTED_CANDIDATE_ONLY)) {
            if (canAddCandidate(asIsCandidate, file)) {
                if (adjustedCandidate == null || !canAddCandidate(
                        adjustedCandidate,
                        file
                    ) || adjustedCandidate.heuristic.isEmpty()
                ) {
                    candidates.add(asIsCandidate!!)
                }
            }
            if (canAddCandidate(adjustedCandidate, file)) {
                candidates.add(adjustedCandidate!!)
            }
        }
        else {
            if (canAddCandidate(asIsCandidate, file)) {
                asIsCandidate?.let { candidates.add(it) }
            }
            if (canAddCandidate(adjustedCandidate, file)) {
                adjustedCandidate?.let { candidates.add(it) }
            }
        }

        return candidates
    }

    fun buildCandidates(efSuggestions: List<EFSuggestion>, editor: Editor, file: PsiFile): List<EFCandidate> {
        val candidates = mutableListOf<EFCandidate>()
        efSuggestions.forEach { candidates.addAll(buildCandidates(it, editor, file)) }
        val resultCandidates = candidates
//        var resultCandidates = EFCandidateUtils.computePopularity(candidates)
//        resultCandidates = EFCandidateUtils.computeHeat(resultCandidates)
        return resultCandidates
    }

    fun buildDistinctCandidates(efSuggestion: EFSuggestion, editor: Editor, file: PsiFile): List<EFCandidate> {
        val candidates = buildCandidates(efSuggestion, editor, file)
        return candidates.distinct()
    }

    fun buildDistinctCandidates(efSuggestions: List<EFSuggestion>, editor: Editor, file: PsiFile): List<EFCandidate> {
        val candidates = mutableListOf<EFCandidate>()
        efSuggestions.forEach { candidates.addAll(buildCandidates(it, editor, file)) }
        return candidates.distinct()
    }

    private fun buildCandidateAsIs(efSuggestion: EFSuggestion, editor: Editor, file: PsiFile): EFCandidate? {
        val psiElementStart = getLeftmostPsiElement(efSuggestion.lineStart - 1, editor, file)
        var psiElementEnd = getLeftmostPsiElement(efSuggestion.lineEnd - 1, editor, file)

        if (psiElementStart == null || psiElementEnd == null) {
            return null
        }

        return EFCandidate(
            functionName = efSuggestion.functionName,
            offsetStart = psiElementStart.startOffset,
            offsetEnd = psiElementEnd.endOffset,
            lineStart = editor.document.getLineNumber(psiElementStart.startOffset) + 1,
            lineEnd = editor.document.getLineNumber(psiElementEnd.endOffset) + 1,
        ).also {
            it.efSuggestion = efSuggestion
            it.type = EfCandidateType.AS_IS
        }
    }

    private fun buildCandidateWithAdjustment(efSuggestion: EFSuggestion, editor: Editor, file: PsiFile): EFCandidate? {
        val psiElementStart = getLeftmostPsiElement(efSuggestion.lineStart - 1, editor, file)
        val psiElementEnd = getLeftmostPsiElement(efSuggestion.lineEnd - 1, editor, file)

        if (psiElementStart == null || psiElementEnd == null) {
            return null
        }

        val adjustedRegion = adjustRegion(psiElementStart, psiElementEnd, file.language) ?: return null
        val adjustedCandidate = EFCandidate(
            functionName = efSuggestion.functionName,
            offsetStart = adjustedRegion.first.startOffset,
            offsetEnd = adjustedRegion.second.endOffset,
            lineStart = editor.document.getLineNumber(adjustedRegion.first.startOffset) + 1,
            lineEnd = editor.document.getLineNumber(adjustedRegion.second.endOffset) + 1,
        ).also {
            it.efSuggestion = efSuggestion
            it.type = EfCandidateType.ADJUSTED
        }

        var resultCandidate = adjustedCandidate
        if (EFSettings.instance.hasHeuristic(EMHeuristic.IF_BODY)) {
            resultCandidate = adjustIfBlockHeuristic(resultCandidate, file)
        }
        if (EFSettings.instance.hasHeuristic(EMHeuristic.PREV_ASSIGNMENT)) {
            resultCandidate = adjustPrevStatementHeuristic(resultCandidate, editor, file)
        }
        return resultCandidate
    }

    private fun canAddCandidate(candidate: EFCandidate?, file: PsiFile): Boolean {
        if (candidate == null) return false
        val hostFunction = PsiUtils.getParentFunctionOrNull(candidate.offsetStart, file)
        if (EFCandidateUtils.candidateSizeIsAboveThreshold(candidate, hostFunction)) return false
        if (EFCandidateUtils.candidateSizeIsBelowThreshold(candidate, hostFunction)) return false

        return true
    }

    /**
     * This is a heuristic to prefer the inner body of an IF statement as opposed to the entire IF statement
     * Example:
     * 10. if (x == 0) {
     * 11.  println(x)
     * 12.  some more statements
     * 13.  more statements
     * 14. }
     * If the candidate is from lines 10 through 14, then another candidate, with lines 11 through 13 will
     * be generated
     */
    private fun adjustIfBlockHeuristic(candidate: EFCandidate, file: PsiFile): EFCandidate {
        val statements = PsiUtils.getStatementsBetweenOffsets(candidate.offsetStart, candidate.offsetEnd, file)
//        val statements = PsiUtils.getTopLevelStatementsBetweenOffsets(candidate.offsetStart, candidate.offsetEnd, file)
        if (statements.isEmpty()) return candidate
        val firstStatement = statements.first()
        val lastStatement = statements.last()
        if (firstStatement is PsiIfStatement && PsiTreeUtil.isAncestor(
                firstStatement,
                lastStatement,
                true
            ) && firstStatement.elseBranch == null
        ) {
            var newOffsetStart = candidate.offsetStart
            var newOffsetEnd = candidate.offsetEnd
            var newLineStart = candidate.lineStart
            var newLineEnd = candidate.lineEnd

            if (firstStatement.lastChild is PsiBlockStatement) {
                val codeBlock = firstStatement.lastChild.lastChild
                if (codeBlock is PsiCodeBlock) {
                    val children = codeBlock.children.drop(1).dropLast(1).filter { it !is PsiWhiteSpace }
                    newOffsetStart = children.first().startOffset
                    newLineStart = children.first().getLineNumber(true) + 1
                    newOffsetEnd = children.last().endOffset
                    newLineEnd = children.last().getLineNumber(false) + 1
                }
            } else if (firstStatement.lastChild is PsiStatement) {
                newOffsetStart = firstStatement.lastChild.startOffset
                newLineStart = firstStatement.lastChild.getLineNumber(true) + 1
                newOffsetEnd = firstStatement.lastChild.endOffset
                newLineEnd = firstStatement.lastChild.getLineNumber(false) + 1
            }
            val newCandidate = EFCandidate(
                functionName = candidate.functionName,
                offsetStart = newOffsetStart,
                offsetEnd = newOffsetEnd,
                lineStart = newLineStart,
                lineEnd = newLineEnd,
            ).also {
                it.efSuggestion = candidate.efSuggestion
                it.type = candidate.type
                it.heuristic = "IF_BLOCK"
            }
            return newCandidate
        }
        return candidate
    }

    /**
     * This is a heuristic that will include the previous line in the candidate if
     * that previous line is a write to a variable that is used in the selection
     */
    private fun adjustPrevStatementHeuristic(candidate: EFCandidate, editor: Editor, file: PsiFile): EFCandidate {
        val statements = PsiUtils.getStatementsBetweenOffsets(candidate.offsetStart, candidate.offsetEnd, file)
        if (statements.isEmpty()) return candidate
        val prevStatement =
            PsiTreeUtil.getPrevSiblingOfType(statements[0], PsiStatement::class.java) ?: return candidate
        if (statements[0].getLineNumber(true) - prevStatement.getLineNumber(false) > 1) return candidate

        val varNames = PsiUtils.getVariableNames(prevStatement)
        if (varNames.isNotEmpty() && PsiUtils.isVariableUsedInStatements(varNames, statements)) {
            val newCandidate = EFCandidate(
                functionName = candidate.functionName,
                offsetStart = prevStatement!!.startOffset,
                offsetEnd = candidate.offsetEnd,
                lineStart = prevStatement.getLineNumber(true) + 1,
                lineEnd = candidate.lineEnd
            ).also {
                it.type = candidate.type
                it.efSuggestion = candidate.efSuggestion
                it.heuristic = "PREV_STATEMENT"
            }
            return newCandidate
        }

        return candidate
    }

    /**
     * Selected region is adjusted by enlarging it to the top-most PsiElement, either start or end
     *
     * Example:
     * 10. if (x == 0) {
     * 11.  println(x)
     * 12.  some more statements
     * 13.  more statements
     * 14. }
     *
     * If the selection has lines 10-13 (included), then it will enlarge the selection to lines 10-14 (included)
     */
    private fun adjustRegion(
        psiElementStart: PsiElement,
        psiElementEnd: PsiElement,
        language: Language
    ): Pair<PsiElement, PsiElement>? {
        var start = psiElementStart
        var end = psiElementEnd

        // both start and end should belong to the same function
        if (!PsiUtils.elementsBelongToSameFunction(start, end, language)) return null

        // if the start/end are inside the argument list, then bubble up to function call
        if (PsiUtils.isElementArgumentOrArgumentList(start, language)) {
            start = PsiUtils.getParentFunctionCallOrNull(start, language) ?: start
        }
        if (PsiUtils.isElementArgumentOrArgumentList(end, language)) {
            end = PsiUtils.getParentFunctionCallOrNull(end, language) ?: end
        }

        // if the start/end are inside the parameter list, then bubble up to function declaration
        if (PsiUtils.isElementParameterOrParameterList(start, language)) {
            start = PsiUtils.getParentFunctionOrNull(start, language) ?: start
        }
        if (PsiUtils.isElementParameterOrParameterList(end, language)) {
            end = PsiUtils.getParentFunctionOrNull(end, language) ?: end
        }

        // if start is parent of end, then assign end to start
        if (PsiTreeUtil.isAncestor(start, end, true)) {
            end = start
        } else if (PsiTreeUtil.isAncestor(end, start, true)) {
            start = end
        }

        // shift right to the first non-brace and non-white space element on the same line
        while ((start.node.elementType == KtTokens.LBRACE || start.node.elementType == JavaTokenType.LBRACE || start is PsiWhiteSpace) &&
            (start.getLineNumber(false) == start.nextSibling?.getLineNumber(false))
        ) {
            start = start.nextSibling
        }

        var commonParent = PsiTreeUtil.findCommonParent(start, end) ?: return null

        if (commonParent == start || commonParent == end || (commonParent !is PsiBlockStatement && commonParent !is PsiCodeBlock && commonParent !is PsiModifiableCodeBlock)) {
            start = commonParent
            end = commonParent
        }

        if (start == end) {
            commonParent = start.parent
        }

        if (commonParent == null) {
            return null
        }

        if (commonParent.lastChild == end) {
            start = commonParent.firstChild
        } else if (commonParent.firstChild == start) {
            end = commonParent.lastChild
        } else {
            start = bubbleUp(start, commonParent)
            end = bubbleUp(end, commonParent)
        }

        if (start.node.elementType == KtTokens.LBRACE || start.node.elementType == JavaTokenType.LBRACE) {
            var temp: PsiElement? = start
            // bubble up the start node to its parent statement
            while (true) {
                if (temp == null) break
                if (temp is PsiStatement && temp !is PsiBlockStatement) break
                if (temp is KtExpression && temp !is KtBlockExpression) break
                temp = temp.parent
            }
            if (temp != null) {
                start = temp
            }
        }

        return start to end
    }


    private fun bubbleUp(element: PsiElement, stopElement: PsiElement): PsiElement {
        var result = element
        while (result.parent != stopElement && result.parent != null) {
            result = result.parent
        }

        return result
    }

    private fun getLeftmostPsiElement(lineNumber: Int, editor: Editor, file: PsiFile): PsiElement? {
        // get the PsiElement on the given lineNumber
        var psiElement: PsiElement = file.findElementAt(editor.document.getLineStartOffset(lineNumber)) ?: return null

        // if there are multiple sibling PsiElements on the same line, look for the first one
        while (psiElement.getLineNumber(false) == psiElement.prevSibling?.getLineNumber(false)) {
            psiElement = psiElement.prevSibling
        }

        // if we are still on a PsiWhiteSpace, then go right
        while (psiElement.getLineNumber(false) == psiElement.nextSibling?.getLineNumber(false) && psiElement is PsiWhiteSpace) {
            psiElement = psiElement.nextSibling
        }

        // if there are multiple parent PsiElements on the same line, look for the top one
        val psiElementLineNumber = psiElement.getLineNumber(false)
        while (true) {
            if (psiElement.parent == null) break
            if (psiElement.parent is PsiCodeBlock || psiElement.parent is KtBlockExpression) break
            if (psiElementLineNumber != psiElement.parent.getLineNumber(false)) break
            psiElement = psiElement.parent
        }

        // move to next non-white space sibling
        while (psiElement is PsiWhiteSpace) {
            psiElement = psiElement.nextSibling
        }

        return psiElement
    }

    private fun isValid(efSuggestion: EFSuggestion, file: PsiFile): Boolean {
        return (efSuggestion.lineStart in (1 until file.getLineCount())) && (efSuggestion.lineEnd in (1 until file.getLineCount()))
    }

    private fun buildInvalidCandidate(efSuggestion: EFSuggestion): EFCandidate {
        return EFCandidate(
            functionName = efSuggestion.functionName,
            lineStart = 0,
            lineEnd = 0,
            offsetStart = -1,
            offsetEnd = -1,
        ).also {
            it.efSuggestion = efSuggestion
            it.type = EfCandidateType.INVALID
        }
    }
}