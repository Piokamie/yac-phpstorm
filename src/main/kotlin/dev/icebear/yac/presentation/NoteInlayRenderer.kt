package dev.icebear.yac.presentation

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import dev.icebear.yac.cli.Note
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints

class NoteInlayRenderer(val note: Note, private val indentX: Int) : EditorCustomElementRenderer {
    val lines: List<String> = NoteText.displayLines(note)

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val metrics = inlay.editor.contentComponent.getFontMetrics(inlay.editor.colorsScheme.getFont(EditorFontType.ITALIC))

        return indentX + textOffset() + (lines.maxOfOrNull { metrics.stringWidth(it) } ?: 0) + JBUI.scale(PADDING)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = inlay.editor.lineHeight * lines.size

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        val editor = inlay.editor
        val graphics = g.create() as Graphics2D
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val left = targetRegion.x + indentX
            val width = targetRegion.width - indentX
            val arc = JBUI.scale(ARC)
            graphics.color = BACKGROUND
            graphics.fillRoundRect(left, targetRegion.y, width, targetRegion.height, arc, arc)
            graphics.color = STRIPE
            graphics.fillRect(left, targetRegion.y, JBUI.scale(STRIPE_WIDTH), targetRegion.height)

            val icon = YacIcons.NOTE
            icon.paintIcon(editor.contentComponent, graphics, left + JBUI.scale(PADDING), targetRegion.y + (editor.lineHeight - icon.iconHeight) / 2)

            graphics.font = editor.colorsScheme.getFont(EditorFontType.ITALIC)
            graphics.color = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT).foregroundColor
                ?: editor.colorsScheme.defaultForeground
            lines.forEachIndexed { index, line ->
                graphics.drawString(line, left + textOffset(), targetRegion.y + index * editor.lineHeight + editor.ascent)
            }
        } finally {
            graphics.dispose()
        }
    }

    private fun textOffset(): Int = JBUI.scale(PADDING) + YacIcons.NOTE.iconWidth + JBUI.scale(ICON_GAP)

    companion object {
        private const val PADDING = 6
        private const val ICON_GAP = 6
        private const val ARC = 6
        private const val STRIPE_WIDTH = 2
        private val BACKGROUND = JBColor(Color(0x35, 0x74, 0xF0, 0x14), Color(0x54, 0x8A, 0xF7, 0x1F))
        private val STRIPE = JBColor(Color(0x35, 0x74, 0xF0, 0x99), Color(0x54, 0x8A, 0xF7, 0x99))
    }
}
