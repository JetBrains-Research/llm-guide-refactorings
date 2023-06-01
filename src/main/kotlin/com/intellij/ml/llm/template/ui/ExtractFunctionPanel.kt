package com.intellij.ml.llm.template.ui

import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.ui.MethodSignatureComponent
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
//import com.jetbrains.php.architecture.complexityMetrics.PhpArchitectureBundle
//import com.jetbrains.php.architecture.complexityMetrics.quickFixes.extractFunction.PhpExtractMethodCandidate
//import com.jetbrains.php.lang.psi.PhpPsiElementFactory
//import com.jetbrains.php.lang.psi.elements.PhpTypedElement
//import com.jetbrains.php.refactoring.extractMethod.PhpExtractMethodDialog.getParameterItems
//import com.jetbrains.php.refactoring.extractMethod.PhpExtractMethodDialog.suggestNames
//import com.jetbrains.php.refactoring.extractMethod.PhpExtractMethodHandler
//import com.jetbrains.php.refactoring.extractMethod.PhpExtractMethodParameterInfo
//import com.jetbrains.php.refactoring.extractMethod.PhpParametersFolder
//import com.jetbrains.php.refactoring.extractMethod.inplace.PhpExtractMethodPopupProvider
//import com.jetbrains.php.refactoring.extractMethod.inplace.PhpInplaceMethodExtractor
//import com.jetbrains.php.refactoring.ui.PhpCodeComponentsFactory
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

class ExtractFunctionPanel(
    project: Project,
    function: PsiMethod,
    editor: Editor,
    candidates: List<ExtractMethodCandidateWithUsageAdapter>,
    highlighter: AtomicReference<ScopeHighlighter>,
) {
    val myExtractFunctionsCandidateTable: JBTable
    val myExtractFunctionsScrollPane: JBScrollPane
    private val myProject: Project = project
    private val myFunction = function
//    val myMethodSignaturePreview: MethodSignatureComponent = PhpCodeComponentsFactory.createPhpMethodSignaturePreview(myProject)
    val myMethodSignaturePreview = MethodSignatureComponent("", project, com.intellij.ide.highlighter.JavaFileType.INSTANCE)
    private val myCandidates = candidates
    private val myEditor = editor
    private val myCandidatesPresentation: Map<ExtractMethodCandidateWithUsageAdapter, String>
    private var myPopup: JBPopup?

    init {
        myMethodSignaturePreview.isFocusable = false
        myMethodSignaturePreview.minimumSize = Dimension(500, 200)
        myMethodSignaturePreview.preferredSize = Dimension(500, 200)
        myMethodSignaturePreview.maximumSize = Dimension(500, 200)
        myPopup = null
        myExtractFunctionsCandidateTable = object : JBTable(ExtractFunctionCandidateTableModel(candidates)) {
            override fun processKeyBinding(ks: KeyStroke, e: KeyEvent, condition: Int, pressed: Boolean): Boolean {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        if (!isEditing && e.modifiersEx == 0) {
                            doExtractMethod(myCandidates[selectedRow])
                        }
                    }
                    e.consume()
                    return true
                }
                if (e.keyCode == KeyEvent.VK_ESCAPE) {
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        myPopup?.cancel()
                    }
                }
                return super.processKeyBinding(ks, e, condition, pressed)
            }

            override fun processMouseEvent(e: MouseEvent?) {
                if (e != null && e.clickCount == 2) {
                    doExtractMethod(myCandidates[selectedRow])
                }
                super.processMouseEvent(e)
            }
        }
        myExtractFunctionsCandidateTable.minimumSize = Dimension(-1, 100)
        myExtractFunctionsCandidateTable.tableHeader = null

        myCandidatesPresentation = HashMap()
        for (it in myCandidates) {
            myCandidatesPresentation[it] = calculateSignature(it.candidate)
        }
        myExtractFunctionsCandidateTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        myExtractFunctionsCandidateTable.selectionModel.addListSelectionListener {
            val phpExtractMethodCandidate = candidates[myExtractFunctionsCandidateTable.selectedRow]
            myMethodSignaturePreview.setSignature(myCandidatesPresentation[phpExtractMethodCandidate])
            val h: ScopeHighlighter = highlighter.get()
            h.dropHighlight()
            val blockFragment = phpExtractMethodCandidate.candidate.fragment
            if (blockFragment != null && blockFragment.isValid) {
                val range: TextRange = blockFragment.textRange
                val element: PsiElement = blockFragment.startElement
                OpenFileDescriptor(element.project, element.containingFile.virtualFile, element.textOffset).navigate(false)
                h.highlight(Pair.create(range, listOf(range)))
            }
        }
        myExtractFunctionsCandidateTable.selectionModel.setSelectionInterval(0, 0)
        myExtractFunctionsScrollPane = JBScrollPane(myExtractFunctionsCandidateTable)
        myExtractFunctionsScrollPane.border = JBUI.Borders.empty()
        myExtractFunctionsScrollPane.maximumSize = Dimension(500, 100)

    }

    fun setDelegatePopup(jbPopup: JBPopup) {
        myPopup = jbPopup
        myExtractFunctionsCandidateTable.columnModel.getColumn(0).cellRenderer = LineNumberRenderer()
        myExtractFunctionsCandidateTable.columnModel.getColumn(0).maxWidth = 50
        myExtractFunctionsCandidateTable.columnModel.getColumn(1).cellRenderer = ExtractCandidateFirstStatementRenderer()
        myExtractFunctionsCandidateTable.setShowGrid(false)
    }

    private fun calculateSignature(phpExtractMethodCandidate: PhpExtractMethodCandidate): String {
        val parametersBuilder = StringBuilder()
        getParameterItems(phpExtractMethodCandidate, phpExtractMethodCandidate.inputVariables,
            phpExtractMethodCandidate.outputVariables, PhpParametersFolder()).joinTo(
            parametersBuilder, ",\n\t") { e -> typeAnnotation(e) + "$" + e.name }

        val suggestNames = suggestNames(myProject, phpExtractMethodCandidate, phpExtractMethodCandidate.outputVariables)
        val functionName =
            if (suggestNames.isEmpty())
                "extracted"
            else
                suggestNames.first()

        val generateCodeText = phpExtractMethodCandidate.fragment.generateCodeText()
        val startText = generateCodeText.split("\n").first()
        val endText = generateCodeText.split("\n").last()
        val functionText = "function $functionName(\n$parametersBuilder\n) ${returnTypePresentation(phpExtractMethodCandidate)} {\n" +
                "\t$startText\n" +
                "\t// method body" +
                "\n\t$endText\n" +
                "}"
        val createFunction = PhpPsiElementFactory.createFunction(myProject, functionText)
        return CodeStyleManager.getInstance(myProject).reformat(createFunction).text
    }

    private fun typeAnnotation(e: PhpExtractMethodParameterInfo): String {
        if (e.typeText.isEmpty()) {
            return ""
        } else {
            return e.typeText.toString() + " "
        }
    }

    private fun returnTypePresentation(phpExtractMethodCandidate: PhpExtractMethodCandidate): String {
        val outputVariables = phpExtractMethodCandidate.outputVariables
        if (outputVariables.size == 0) {
            return ":void"
        }
        if (outputVariables.size == 1) {
            val first = outputVariables.first()
            if (first is PhpTypedElement) {

                val returnTypePresentation = PhpExtractMethodHandler.getReturnTypePresentation(myProject, first.type, myFunction)
                if (returnTypePresentation != null) {
                    return ":$returnTypePresentation"
                }
                else {
                    return ""
                }
            }
        }
        return ""
    }

    fun createPanel(): JComponent {
        // IDEA-318533 Port PhpExtractFunctionPanel to Kotlin UI DSL 2
        val popupPanel = panel {
            row {
                cell(myExtractFunctionsScrollPane).align(AlignX.FILL)
            }

            row {
                cell(myMethodSignaturePreview)
                    .align(AlignX.FILL)
                    .applyToComponent { minimumSize = JBDimension(100, 100) }
            }

            row {
                button(PhpArchitectureBundle.message("extract.class.popup.window.button"), actionListener = {
                    val selectedBlockFragment = myCandidates[myExtractFunctionsCandidateTable.selectedRow]
                    myPopup?.closeOk(null)
                    doExtractMethod(selectedBlockFragment)
                }).
                comment(PhpArchitectureBundle.message(
                    "extract.class.popup.label.to.extract.any.other.piece.code.select.it.in.editor.invoke.extract.method.refactoring.0",
                    KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction("ExtractMethod"))))
            }
        }
        popupPanel.preferredFocusedComponent = myExtractFunctionsCandidateTable
        return popupPanel
    }

    fun doExtractMethod(selectedBlockFragment: ExtractMethodCandidateWithUsageAdapter) {
        myPopup!!.cancel()
        val fragment = selectedBlockFragment.candidate.fragment
        if (!myEditor.selectionModel.hasSelection()) {
            myEditor.selectionModel.setSelection(fragment.startOffset, fragment.endOffset)
        }
        val file: PsiFile = fragment.file
        val settings = PhpExtractMethodHandler.getSettingsForInplace(fragment)
        val inplaceMethodExtractor = PhpInplaceMethodExtractor(myProject, file, myEditor, fragment, settings, PhpExtractMethodPopupProvider())
        val runnable = Runnable {
            executeRefactoringCommand(myProject) {
                inplaceMethodExtractor.performInplaceRefactoring(ContainerUtil.newLinkedHashSet("extracted"))
            }
        }
        runnable.run()
    }
    private fun executeRefactoringCommand(project: Project, command: () -> Unit){
        CommandProcessor.getInstance().executeCommand(project, command, PhpExtractMethodHandler.getRefactoringName(), null)
    }

}