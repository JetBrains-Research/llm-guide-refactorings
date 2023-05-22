package com.intellij.ml.llm.template.utils

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.PsiModifier.ModifierConstant
import com.intellij.refactoring.IntroduceVariableUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import com.intellij.refactoring.extractMethod.PrepareFailedException
import com.intellij.util.ArrayUtilRt
import com.intellij.util.IncorrectOperationException
import junit.framework.TestCase

class EFHelper {
}