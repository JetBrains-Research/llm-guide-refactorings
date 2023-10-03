package com.intellij.ml.llm.template.utils

import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.llm.template.LLMBundle
import com.intellij.ml.llm.template.customextractors.MyInplaceExtractionHelper
import com.intellij.ml.llm.template.extractfunction.EFCandidate
import com.intellij.ml.llm.template.extractfunction.MethodExtractionType
import com.intellij.ml.llm.template.models.FunctionNameProvider
import com.intellij.ml.llm.template.models.MyMethodExtractor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ExtractKotlinFunctionHandler

class CodeTransformer(val extractFunctionType: MethodExtractionType = MethodExtractionType.DIALOG) : Observable() {
    private val logger = Logger.getInstance("#com.intellij.ml.llm")

    fun applyCandidate(efCandidate: EFCandidate, project: Project, editor: Editor, file: PsiFile): Boolean {
        var applicationResult = EFApplicationResult.OK
        var reason = ""

        if (!isCandidateValid(efCandidate)) {
            applicationResult = EFApplicationResult.FAIL
            reason = "invalid extract function candidate"
        } else {
            try {
                editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
                invokeExtractFunction(efCandidate.functionName, project, editor, file)
            } catch (e: Exception) {
                applicationResult = EFApplicationResult.FAIL
                reason = e.message ?: ""
            } catch (e: Error) {
                applicationResult = EFApplicationResult.FAIL
                reason = e.message ?: ""
            }
        }

        notifyObservers(
            EFNotification(
                EFCandidateApplicationPayload(
                    result = applicationResult,
                    reason = reason,
                    candidate = efCandidate
                )
            )
        )

        return applicationResult == EFApplicationResult.OK
    }

    fun applyCandidate2(efCandidate: EFCandidate, project: Project, editor: Editor, file: PsiFile): Boolean {
        var applicationResult = EFApplicationResult.OK
        var reason = ""

        if (!isCandidateValid(efCandidate)) {
            applicationResult = EFApplicationResult.FAIL
            reason = "invalid extract function candidate"
        }
        else {
            try {
                editor.selectionModel.setSelection(efCandidate.offsetStart, efCandidate.offsetEnd)
                invokeExtractFunction(efCandidate.functionName, project, editor, file)

                if (selectionIsEntireBodyFunctionJava(efCandidate, file)) {
                    applicationResult = EFApplicationResult.FAIL
                    reason = LLMBundle.message("extract.function.entire.function.selection.message")
                }
                else if (selectionIsOneLiner(efCandidate)) {
                    applicationResult = EFApplicationResult.FAIL
                    reason = "Selection is one line"
                }
            } catch (e: Exception) {
                applicationResult = EFApplicationResult.FAIL
                reason = e.message ?: ""
            } catch (e: Error) {
                applicationResult = EFApplicationResult.FAIL
                reason = e.message ?: ""
            }
        }

        notifyObservers(
            EFNotification(
                EFCandidateApplicationPayload(
                    result = applicationResult,
                    reason = reason,
                    candidate = efCandidate
                )
            )
        )

        return applicationResult == EFApplicationResult.OK
    }

    private fun invokeExtractFunction(newFunctionName: String, project: Project, editor: Editor?, file: PsiFile?) {
        val functionNameProvider = FunctionNameProvider(newFunctionName)
        when (file?.language) {
            JavaLanguage.INSTANCE -> {
                MyMethodExtractor.invokeOnElements(
                    project, editor, file, findSelectedPsiElements(editor, file), functionNameProvider, extractFunctionType
                )
            }

            KotlinLanguage.INSTANCE -> {
                val dataContext = (editor as EditorEx).dataContext
                val allContainersEnabled = false
                val inplaceExtractionHelper = MyInplaceExtractionHelper(allContainersEnabled, functionNameProvider)
                ExtractKotlinFunctionHandler(allContainersEnabled, inplaceExtractionHelper).invoke(
                    project, editor, file, dataContext
                )
            }
        }
    }

    private fun findSelectedPsiElements(editor: Editor?, file: PsiFile?): Array<PsiElement> {
        if (editor == null) {
            return emptyArray()
        }
        val selectionModel = editor.selectionModel
        val startOffset = selectionModel.selectionStart
        val endOffset = selectionModel.selectionEnd

        val startElement = file?.findElementAt(startOffset)
        val endElement = file?.findElementAt(if (endOffset > 0) endOffset - 1 else endOffset)

        if (startElement == null || endElement == null) {
            return emptyArray()
        }

        val commonParent = PsiTreeUtil.findCommonParent(startElement, endElement) ?: return emptyArray()

        val selectedElements = PsiTreeUtil.findChildrenOfType(commonParent, PsiElement::class.java)
        val result = selectedElements.filter {
            it.textRange.startOffset >= startOffset && it.textRange.endOffset <= endOffset
        }.toTypedArray()
        return result
    }

    private fun isCandidateValid(efCandidate: EFCandidate): Boolean {
        return efCandidate.offsetStart >= 0 && efCandidate.offsetEnd >= 0
    }
}