package com.intellij.ml.llm.template.ui

import javax.swing.SwingConstants
import javax.swing.table.DefaultTableCellRenderer

class FunctionNameTableCellRenderer: DefaultTableCellRenderer() {
    init {
        horizontalAlignment = SwingConstants.LEFT
    }
}