package com.intellij.ml.llm.template.utils

import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.EFSuggestion
import com.intellij.ml.llm.template.extractfunction.EfCandidateType
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.idea.base.psi.getLineNumber

class EFCandidateFactory {
    fun buildCandidates(efSuggestion: EFSuggestion, editor: Editor, file: PsiFile): HashSet<EFCandidate> {
        val candidates = HashSet<EFCandidate>()

        if (! isValid(efSuggestion)) {
            buildInvalidCandidate(efSuggestion).let { candidates.add(it) }
            return candidates
        }

        buildCandidateAsIs(efSuggestion, editor, file)?.let { candidates.add(it) }
        buildCandidateWithAdjustment(efSuggestion, editor, file)?.let { candidates.add(it) }

        return candidates
    }

    private fun buildCandidateAsIs(efSuggestion: EFSuggestion, editor: Editor, file: PsiFile): EFCandidate? {
        val psiElementStart = getLeftmostPsiElement(efSuggestion.lineStart, editor, file)
        val psiElementEnd = getLeftmostPsiElement(efSuggestion.lineEnd, editor, file)
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
        val psiElementStart = getLeftmostPsiElement(efSuggestion.lineStart, editor, file)
        val psiElementEnd = getLeftmostPsiElement(efSuggestion.lineEnd, editor, file)

        if (psiElementStart == null || psiElementEnd == null) {
            return null
        }

        val adjustedRegion = adjustRegion(psiElementStart, psiElementEnd) ?: return null
        return EFCandidate(
            functionName = efSuggestion.functionName,
            offsetStart = adjustedRegion.first.startOffset,
            offsetEnd = adjustedRegion.second.endOffset,
            lineStart = editor.document.getLineNumber(adjustedRegion.first.startOffset) + 1,
            lineEnd = editor.document.getLineNumber(adjustedRegion.second.endOffset) + 1,
        ).also {
            it.efSuggestion = efSuggestion
            it.type = EfCandidateType.ADJUSTED
        }
    }


    /**
     * Selected region is adjusted by enlarging it to the top-most PsiElement, either start or end
     */
    private fun adjustRegion(psiElementStart: PsiElement, psiElementEnd: PsiElement): Pair<PsiElement, PsiElement>? {
        var commonParent = PsiTreeUtil.findCommonParent(psiElementStart, psiElementEnd)
        var start = psiElementStart
        var end = psiElementEnd

        if (commonParent == start || commonParent == end) {
            start = commonParent
            end = commonParent
        }

        if (start == end) {
            commonParent = start.parent
        }

        if (commonParent == null) {
            return null
        }

        start = bubbleUp(start, commonParent)
        end = bubbleUp(end, commonParent)

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
        var psiElement = file.findElementAt(editor.document.getLineStartOffset(lineNumber)) ?: return null

        // if there are multiple sibling PsiElements on the same line, look for the first one
        while (psiElement.getLineNumber(true) == psiElement.prevSibling?.getLineNumber(true)) {
            psiElement = psiElement.prevSibling
        }

        // if there are multiple parent PsiElements on the same line, look for the top one
        while (psiElement.getLineNumber(true) == psiElement.parent?.getLineNumber(true)) {
            psiElement = psiElement.parent
        }

        if (psiElement is PsiWhiteSpace) {
            psiElement = psiElement.prevSibling
        }

        return psiElement
    }

    private fun isValid(efSuggestion: EFSuggestion): Boolean {
        return efSuggestion.lineStart > 0 && efSuggestion.lineEnd > 0
    }

    private fun buildInvalidCandidate(efSuggestion: EFSuggestion) : EFCandidate {
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