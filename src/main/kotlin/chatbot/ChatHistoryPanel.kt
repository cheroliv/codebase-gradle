package chatbot

import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.GeneralPath
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

// ── Données ──────────────────────────────────────────────────────────────────

data class BranchRequest(
    val turnIndex: Int,
    val fromUser: Boolean,
    val originalMessage: String,
    val editedMessage: String,
)

// ── Constantes de design ──────────────────────────────────────────────────────

private object Theme {
    val userBubble = Color(0x534AB7)
    val userText = Color(0xEEEDFE)
    val aiBubble = Color(0xF4F4F2)
    val aiText = Color(0x1A1A18)
    val border = Color(0xD3D1C7)
    val actionBg = Color.WHITE
    val actionFg = Color(0x5F5E5A)
    val branchFg = Color(0x534AB7)
    val branchBdr = Color(0xAFA9EC)
    val meta = Color(0x888780)
    val avatarAi = Color(0xE8E6E0)
    val avatarUser = Color(0x534AB7)

    const val bubbleArc = 16
    const val maxBubbleW = 520
    const val avatarSize = 28
    const val hGap = 10
    const val vGap = 8
    const val panelPad = 16

    val models = arrayOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-haiku-4-5")
}

// ── ChatHistoryPanel ──────────────────────────────────────────────────────────

/**
 * Composant d'historique de chatbot moderne.
 *
 * Utilisation :
 *   val panel = ChatHistoryPanel()
 *   panel.onBranchRequested = { req, model -> ... }
 *   panel.addTurn("Question ?", "Réponse.")
 *   monJPanel.add(panel, BorderLayout.CENTER)
 */
class ChatHistoryPanel : JPanel(BorderLayout()) {

    /** Callback déclenché quand l'utilisateur ouvre une nouvelle branche. */
    var onBranchRequested: ((BranchRequest, String) -> Unit)? = null

    private val turns = mutableListOf<ChatTurn>()
    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = EmptyBorder(Theme.panelPad, Theme.panelPad, Theme.panelPad, Theme.panelPad)
    }

    private val scroll = JScrollPane(
        contentPanel,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    ).apply {
        border = null
        isOpaque = false
        viewport.isOpaque = false
        verticalScrollBar.unitIncrement = 16
    }

    init {
        isOpaque = false
        add(scroll, BorderLayout.CENTER)
    }

    // ── API publique ──────────────────────────────────────────────────────────

    @JvmOverloads
    fun addTurn(userMessage: String, aiMessage: String, timestamp: String? = null) {
        val turn = ChatTurn(userMessage, aiMessage, timestamp, turns.size, this)
        turns += turn
        contentPanel.add(turn)
        contentPanel.add(Box.createVerticalStrut(20))
        revalidate()
        scrollToBottom()
    }

    fun clear() {
        turns.clear()
        contentPanel.removeAll()
        revalidate()
        repaint()
    }

    internal fun fireBranch(req: BranchRequest, model: String) =
        onBranchRequested?.invoke(req, model)

    private fun scrollToBottom() = SwingUtilities.invokeLater {
        scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum
    }
}

// ── ChatTurn ──────────────────────────────────────────────────────────────────

private class ChatTurn(
    private val userMsg: String,
    private val aiMsg: String,
    timestamp: String?,
    private val index: Int,
    private val parent: ChatHistoryPanel,
) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT

        timestamp?.let { add(buildTimestamp(it)) }

        add(buildRow(userMsg, isUser = true))
        add(buildActions(userMsg, isUser = true))
        add(Box.createVerticalStrut(Theme.vGap))
        add(buildRow(aiMsg, isUser = false))
        add(buildActions(aiMsg, isUser = false))
    }

    // ── Timestamp ─────────────────────────────────────────────────────────────

    private fun buildTimestamp(text: String) = JPanel(BorderLayout(8, 0)).apply {
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        border = EmptyBorder(0, 0, 8, 0)
        add(JSeparator().apply { foreground = Theme.border }, BorderLayout.WEST)
        add(JLabel(text, SwingConstants.CENTER).apply {
            font = font.deriveFont(10f)
            foreground = Theme.meta
        }, BorderLayout.CENTER)
        add(JSeparator().apply { foreground = Theme.border }, BorderLayout.EAST)
    }

    // ── Ligne avatar + bulle ──────────────────────────────────────────────────

    private fun buildRow(text: String, isUser: Boolean): JPanel {
        val bubble = BubbleLabel(text, isUser)
        val avatar = AvatarLabel(if (isUser) "M" else "AI", isUser)
        return JPanel(FlowLayout(if (isUser) FlowLayout.RIGHT else FlowLayout.LEFT, Theme.hGap, 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            if (isUser) {
                add(bubble); add(avatar)
            } else {
                add(avatar); add(bubble)
            }
        }
    }

    // ── Barre d'actions ───────────────────────────────────────────────────────

    private fun buildActions(message: String, isUser: Boolean): JPanel {
        val bar = JPanel(FlowLayout(if (isUser) FlowLayout.RIGHT else FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(2, Theme.avatarSize + Theme.hGap, 0, Theme.avatarSize + Theme.hGap)
            isVisible = false
        }

        val copyBtn = smallButton("Copier", "⎘", Theme.actionBg, Theme.actionFg, Theme.border)
        copyBtn.addActionListener { copyToClipboard(message) }

        val branchBtn = smallButton("Nouvelle branche", "⎇", Theme.actionBg, Theme.branchFg, Theme.branchBdr)
        branchBtn.addActionListener { openBranchDialog(message, isUser) }

        bar.add(copyBtn)
        bar.add(branchBtn)

        val hoverListener = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                bar.isVisible = true
            }

            override fun mouseExited(e: MouseEvent) {
                val root = SwingUtilities.getRoot(this@ChatTurn) ?: return
                val hovered = SwingUtilities.getDeepestComponentAt(root, e.xOnScreen, e.yOnScreen)
                if (!SwingUtilities.isDescendingFrom(hovered, this@ChatTurn)) bar.isVisible = false
            }
        }
        addMouseListener(hoverListener)
        bar.addMouseListener(hoverListener)

        return bar
    }

    // ── Dialogue branche ──────────────────────────────────────────────────────

    private fun openBranchDialog(message: String, isUser: Boolean) {
        val owner = SwingUtilities.getWindowAncestor(this)
        BranchDialog(owner, message) { edited, model ->
            parent.fireBranch(BranchRequest(index, isUser, message, edited), model)
        }.isVisible = true
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private fun copyToClipboard(text: String) =
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

    private fun smallButton(
        label: String, icon: String,
        bg: Color, fg: Color, bdr: Color,
    ) = object : JButton("$icon $label") {
        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = if (model.isRollover) bg.darker() else bg
            g2.fillRoundRect(0, 0, width, height, 8, 8)
            super.paintComponent(g2)
            g2.dispose()
        }

        override fun paintBorder(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = bdr
            g2.drawRoundRect(0, 0, width - 1, height - 1, 8, 8)
            g2.dispose()
        }
    }.apply {
        font = font.deriveFont(Font.PLAIN, 11f)
        foreground = fg
        background = bg
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        margin = Insets(3, 8, 3, 8)
    }
}

// ── BubbleLabel ───────────────────────────────────────────────────────────────

private class BubbleLabel(text: String, private val isUser: Boolean) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        val ta = JTextArea(text).apply {
            isEditable = false
            isOpaque = false
            font = UIManager.getFont("Label.font").deriveFont(14f)
            foreground = if (isUser) Theme.userText else Theme.aiText
            lineWrap = true
            wrapStyleWord = true
            border = EmptyBorder(10, 14, 10, 14)
        }
        val prefW = minOf(ta.preferredSize.width + 28, Theme.maxBubbleW)
        ta.preferredSize = Dimension(prefW, ta.preferredScrollableViewportSize.height + 20)
        add(ta, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val shape = roundedRectExcept(
            x = 0f, y = 0f, w = width.toFloat(), h = height.toFloat(),
            r = Theme.bubbleArc.toFloat(),
            squareTL = !isUser, squareTR = false,
            squareBR = isUser, squareBL = false,
        )
        g2.color = if (isUser) Theme.userBubble else Theme.aiBubble
        g2.fill(shape)
        if (!isUser) {
            g2.color = Theme.border
            g2.stroke = BasicStroke(0.8f)
            g2.draw(shape)
        }
        g2.dispose()
        super.paintComponent(g)
    }

    /** Rect arrondi avec coin(s) optionnellement carrés. */
    private fun roundedRectExcept(
        x: Float, y: Float, w: Float, h: Float, r: Float,
        squareTL: Boolean, squareTR: Boolean, squareBR: Boolean, squareBL: Boolean,
    ) = GeneralPath().apply {
        moveTo(x + if (squareTL) 0f else r, y)
        lineTo(x + w - if (squareTR) 0f else r, y)
        if (!squareTR) quadTo(x + w, y, x + w, y + r)
        lineTo(x + w, y + h - if (squareBR) 0f else r)
        if (!squareBR) quadTo(x + w, y + h, x + w - r, y + h)
        lineTo(x + if (squareBL) 0f else r, y + h)
        if (!squareBL) quadTo(x, y + h, x, y + h - r)
        lineTo(x, y + if (squareTL) 0f else r)
        if (!squareTL) quadTo(x, y, x + r, y)
        closePath()
    }
}

// ── AvatarLabel ───────────────────────────────────────────────────────────────

private class AvatarLabel(
    private val initials: String,
    private val isUser: Boolean,
) : JPanel() {

    init {
        isOpaque = false
        val sz = Dimension(Theme.avatarSize, Theme.avatarSize)
        preferredSize = sz; maximumSize = sz; minimumSize = sz
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val s = Theme.avatarSize
        g2.color = if (isUser) Theme.avatarUser else Theme.avatarAi
        g2.fillOval(0, 0, s, s)
        g2.color = Theme.border
        g2.stroke = BasicStroke(0.8f)
        g2.drawOval(0, 0, s - 1, s - 1)
        g2.color = if (isUser) Theme.userText else Theme.meta
        g2.font = g2.font.deriveFont(Font.BOLD, 10f)
        val fm = g2.fontMetrics
        g2.drawString(initials, (s - fm.stringWidth(initials)) / 2, (s - fm.height) / 2 + fm.ascent)
        g2.dispose()
    }
}

// ── BranchDialog ──────────────────────────────────────────────────────────────

private class BranchDialog(
    owner: Window?,
    originalMessage: String,
    private val onConfirm: (editedMessage: String, model: String) -> Unit,
) : JDialog(owner, "Nouvelle branche", ModalityType.APPLICATION_MODAL) {

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE

        val root = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(20, 20, 20, 20)
        }

        val preview = JLabel(
            "<html><i>Depuis :</i> ${originalMessage.take(100).escapeHtml()}…</html>"
        ).apply {
            font = font.deriveFont(12f)
            foreground = Theme.meta
            border = CompoundBorder(LineBorder(Theme.border, 1, true), EmptyBorder(8, 10, 8, 10))
            alignmentX = LEFT_ALIGNMENT
        }

        val editLabel = JLabel("Message de relance").apply {
            font = font.deriveFont(12f); alignmentX = LEFT_ALIGNMENT
        }
        val editArea = JTextArea(originalMessage, 4, 30).apply {
            lineWrap = true; wrapStyleWord = true
            font = font.deriveFont(13f)
            border = EmptyBorder(6, 8, 6, 8)
        }
        val editScroll = JScrollPane(editArea).apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 100)
            border = LineBorder(Theme.border, 1, true)
        }

        val modelLabel = JLabel("Modèle").apply {
            font = font.deriveFont(12f); alignmentX = LEFT_ALIGNMENT
        }
        val modelCombo = JComboBox(Theme.models).apply {
            selectedIndex = 1
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 30)
            font = font.deriveFont(13f)
        }

        val cancelBtn = JButton("Annuler").apply { addActionListener { dispose() } }
        val startBtn = JButton("Démarrer").apply {
            background = Theme.userBubble
            foreground = Theme.userText
            isOpaque = true; isBorderPainted = false
            addActionListener {
                val edited = editArea.text.trim()
                val model = modelCombo.selectedItem as String
                if (edited.isNotEmpty()) {
                    onConfirm(edited, model); dispose()
                }
            }
        }

        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false; alignmentX = LEFT_ALIGNMENT
            add(cancelBtn); add(startBtn)
        }

        with(root) {
            add(preview)
            add(Box.createVerticalStrut(12))
            add(editLabel)
            add(Box.createVerticalStrut(4))
            add(editScroll)
            add(Box.createVerticalStrut(12))
            add(modelLabel)
            add(Box.createVerticalStrut(4))
            add(modelCombo)
            add(Box.createVerticalStrut(16))
            add(btnRow)
        }

        contentPane = root
        pack()
        isResizable = false
        setLocationRelativeTo(owner)
    }

    private fun String.escapeHtml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

// ── main de démonstration ─────────────────────────────────────────────────────

fun main() = SwingUtilities.invokeLater {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val chat = ChatHistoryPanel().apply {
        onBranchRequested = { req, model ->
            println("=== Nouvelle branche ===")
            println("Tour         : ${req.turnIndex}")
            println("Depuis user  : ${req.fromUser}")
            println("Msg original : ${req.originalMessage}")
            println("Msg édité    : ${req.editedMessage}")
            println("Modèle       : $model")
        }

        addTurn(
            "Explique-moi le fonctionnement d'un transformeur en deep learning.",
            "Un transformeur repose sur le mécanisme d'attention multi-têtes. Pour chaque token en entrée, " +
                    "on calcule trois vecteurs : Query, Key et Value. L'attention pondère l'importance de chaque " +
                    "position par rapport aux autres, ce qui permet des dépendances à longue portée sans récurrence.",
            "Il y a 10 min",
        )
        addTurn(
            "Quel est le rôle du positional encoding ?",
            "Le positional encoding injecte l'information de position dans les embeddings, car l'attention " +
                    "est par nature permutation-invariante. On additionne des sinusoïdes de fréquences variées à " +
                    "chaque vecteur, permettant au modèle d'apprendre les relations d'ordre entre tokens.",
        )
        addTurn(
            "Peut-on remplacer le positional encoding par des embeddings appris ?",
            "Oui, les embeddings de position appris sont courants dans des architectures comme BERT ou GPT. " +
                    "Ils sont plus flexibles mais ne généralisent pas au-delà de la longueur de séquence " +
                    "d'entraînement, contrairement aux encodages sinusoïdaux.",
        )
    }

    JFrame("Chat History — Démo Kotlin").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        setSize(760, 600)
        add(chat, BorderLayout.CENTER)
        setLocationRelativeTo(null)
        isVisible = true
    }
}