/*
 * Copyright © 2026 msmt2018
 * SPDX-License-Identifier: Apache-2.0
 */
package x.editor.app.symbol

import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolTextClipboard
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolTextDocument
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolTextDocumentActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.action.SymbolActionHost
import com.zerostudio.idea.widget.editor.symbolinput.v2.model.ClipboardOperation
import x.editor.app.view.CodeEditorView
import x.github.module.piecetable.common.Range

/**
 * Bridges the current X Editor document to the editor-neutral V2 action runtime.
 *
 * All offsets exposed to the symbol library are UTF-16 offsets. Small range reads
 * stay on the PieceTree-backed EditorBuffer; the whole document is never copied
 * merely to perform a local symbol action.
 */
internal fun createCodeEditorSymbolActionHost(
    editor: CodeEditorView?,
    openManager: () -> Boolean
): SymbolActionHost? {
    editor ?: return null

    val document = object : SymbolTextDocument {
        override val isEditable: Boolean
            get() = editor.isEditable()

        override val textLength: Int
            get() = editor.getBuffer().length

        override val lineCount: Int
            get() = editor.getLineCount()

        override val cursorOffset: Int
            get() = editor.getOffset(editor.getCursorLine(), editor.getCursorColumn())

        override val selectionStart: Int
            get() {
                val selection = editor.getSelection() ?: return cursorOffset
                return editor.getOffset(selection.startLine, selection.startColumn)
            }

        override val selectionEnd: Int
            get() {
                val selection = editor.getSelection() ?: return cursorOffset
                return editor.getOffset(selection.endLine, selection.endColumn)
            }

        override fun lineForOffset(offset: Int): Int =
            (editor.getPosition(offset.coerceIn(0, textLength)).lineNumber - 1)
                .coerceIn(0, (lineCount - 1).coerceAtLeast(0))

        override fun lineStartOffset(line: Int): Int =
            editor.getLineStartOffset((line + 1).coerceIn(1, lineCount.coerceAtLeast(1)))

        override fun lineEndOffset(line: Int): Int =
            editor.getLineEndOffset((line + 1).coerceIn(1, lineCount.coerceAtLeast(1)))

        override fun lineText(line: Int): String =
            editor.getLine((line + 1).coerceIn(1, lineCount.coerceAtLeast(1)))

        override fun textBetween(start: Int, end: Int): String {
            val safeStart = start.coerceIn(0, textLength)
            val safeEnd = end.coerceIn(safeStart, textLength)
            val from = editor.getPosition(safeStart)
            val to = editor.getPosition(safeEnd)
            return editor.getValueInRange(
                Range(from.lineNumber, from.column, to.lineNumber, to.column)
            )
        }

        override fun charAt(offset: Int): Char {
            require(offset in 0 until textLength)
            return editor.getBuffer().getCharCode(offset).toChar()
        }

        override fun replace(start: Int, end: Int, replacement: String): Boolean {
            if (!isEditable || start !in 0..textLength || end !in start..textLength) return false
            val from = editor.getPosition(start)
            val to = editor.getPosition(end)
            return editor.applyTextEdits(
                listOf(Range(from.lineNumber, from.column, to.lineNumber, to.column) to replacement)
            )
        }

        override fun setSelection(start: Int, end: Int) {
            val safeStart = start.coerceIn(0, textLength)
            val safeEnd = end.coerceIn(0, textLength)
            val from = editor.getPosition(safeStart)
            val to = editor.getPosition(safeEnd)
            editor.setSelection(
                Range(from.lineNumber, from.column, to.lineNumber, to.column)
            )
        }
    }

    return SymbolTextDocumentActionHost(
        document = document,
        clipboard = SymbolTextClipboard { _, operation ->
            when (operation) {
                ClipboardOperation.COPY -> editor.copy()
                ClipboardOperation.CUT -> editor.cut()
                ClipboardOperation.PASTE -> editor.paste()
            }
        },
        openManagerAction = openManager
    )
}
