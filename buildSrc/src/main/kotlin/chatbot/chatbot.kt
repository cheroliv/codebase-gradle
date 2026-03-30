package chatbot

import codebase.CodebaseConfiguration
import codebase.availableModels
import codebase.embedsYmlConfig
import io.javelit.components.layout.ColumnsComponent
import io.javelit.core.Jt
import io.javelit.core.JtComponent
import java.io.File


// ── setupSidebar ──────────────────────────────────────────────────────────────
//
// Sidebar commune aux deux vues.
// Rendu dans Jt.SIDEBAR — appelée en tête de chatbotApp, avant le dispatch.
// "New Chat" et les liens de navigation sont statiques (Javelit ne supporte
// pas de routing multi-page fiable sans switchPage).
//
fun setupSidebar() {
    val sidebar = Jt.SIDEBAR
    val state = Jt.sessionState()

    // ── Logo ──────────────────────────────────────────────────────────────────
    Jt.html(
        """
        <div style="padding:18px 14px 10px 14px;">
          <div style="display:flex;align-items:center;gap:10px;">
            <div style="background:#1a3a6b;border-radius:8px;width:34px;height:34px;
                display:flex;align-items:center;justify-content:center;
                color:white;font-size:1.1em;font-weight:700;">✦</div>
            <span style="font-weight:700;font-size:1.05em;color:#1a1a2e;
                letter-spacing:0.01em;">Assistant</span>
          </div>
        </div>
    """.trimIndent()
    ).use(sidebar)

    // ── New Conversation — form actif ─────────────────────────────────────────
    val newConvForm = Jt.form().key("sidebar-new-conv").use(sidebar)
    Jt.formSubmitButton("➕ New Conversation").use(newConvForm)

    Jt.markdown("---").use(sidebar)

    // ── Navigation statique ───────────────────────────────────────────────────
    // "New Chat" surligné si page == "chat"
    val currentPage = state.getString("page") ?: "chat"
    val newChatBg = if (currentPage == "chat") "#e8f0fe" else "transparent"
    Jt.html(
        """
        <div style="background:$newChatBg;border-radius:6px;padding:7px 10px;
            margin:2px 0;display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#1a1a2e;cursor:default;">
          <span style="color:#1a3a6b;font-weight:600;">＋</span> New Chat
        </div>
        <div style="border-radius:6px;padding:7px 10px;margin:2px 0;
            display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#444;cursor:default;">
          🕐 History
        </div>
        <div style="border-radius:6px;padding:7px 10px;margin:2px 0;
            display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#444;cursor:default;">
          📖 Knowledge Base
        </div>
        <div style="border-radius:6px;padding:7px 10px;margin:2px 0;
            display:flex;align-items:center;gap:8px;
            font-size:0.93em;color:#444;cursor:default;">
          ⚙️ Settings
        </div>
    """.trimIndent()
    ).use(sidebar)

    // ── Handler New Conversation ──────────────────────────────────────────────
    if (Jt.componentsState()["sidebar-new-conv"] == true) {
        @Suppress("UNCHECKED_CAST")
        val history = state["history"] as? MutableList<Triple<String, String, String>>
        history?.clear()
        state["page"] = "chat"
        state["pendingMessage"] = ""
        state["wordCount"] = 0
        state["pendingToolCall"] = ""
        state["toolCallArgs"] = ""
        state["searchOpen"] = false
        state["searchTerm"] = ""
        state["searchIndex"] = 0
        state["enrichRawPrompt"] = ""
        state["enrichedPrompt"] = ""
        state["enrichEmbeds"] = mutableListOf<String>()
        state["playwrightStatus"] = "idle"
        Jt.rerun()
    }
}


// ── chatbotApp ────────────────────────────────────────────────────────────────
//
// Deux vues pilotées par sessionState["page"] :
//   "chat"   → vue conversation principale
//   "enrich" → vue enrichissement du prompt
//
// Règles Javelit : pas de switchPage — rendu conditionnel if/else sur "page".
//
fun chatbotApp(
    cfg: CodebaseConfiguration,
    providerName: String
) {
    val state = Jt.sessionState()

    // ── Session state — init ──────────────────────────────────────────────────
    state.putIfAbsent("history", mutableListOf<Triple<String, String, String>>())
    state.putIfAbsent("page", "chat")
    state.putIfAbsent("pendingMessage", "")
    state.putIfAbsent("wordCount", 0)
    state.putIfAbsent("playwrightStatus", "idle")
    state.putIfAbsent("pendingToolCall", "")
    state.putIfAbsent("toolCallArgs", "")
    state.putIfAbsent("searchOpen", false)
    state.putIfAbsent("searchTerm", "")
    state.putIfAbsent("searchIndex", 0)
    state.putIfAbsent("enrichRawPrompt", "")
    state.putIfAbsent("enrichedPrompt", "")
    state.putIfAbsent("enrichProvider", "ollama")
    state.putIfAbsent("enrichModel", "")
    state.putIfAbsent("enrichEmbeds", mutableListOf<String>())

    @Suppress("UNCHECKED_CAST")
    val history = state.get("history") as MutableList<Triple<String, String, String>>

    val currentPage = state.getString("page") ?: "chat"

    val models = availableModels(cfg, providerName)
    val defaultModel = models.firstOrNull() ?: "no-model"
    state.putIfAbsent("selectedModel", defaultModel)
    val currentModel = state.getString("selectedModel") ?: defaultModel
    val currentIndex = models.indexOf(currentModel).takeIf { it >= 0 } ?: 0

    val busy = (state.getString("playwrightStatus") ?: "idle") == "waiting"

    // ── Sidebar — rendue avant le dispatch ────────────────────────────────────
    setupSidebar()

    // ── Dispatch vue ──────────────────────────────────────────────────────────
    if (currentPage == "enrich") {
        val projectDir = File(System.getProperty("user.dir"))
        val rawPrompt = state.getString("enrichRawPrompt") ?: ""
        val enrichedPrompt = state.getString("enrichedPrompt") ?: rawPrompt

        // ── Header "← Back to Chat   Enrichir" ───────────────────────────────────
        val headerBar = Jt.columns(2)
            .widths(listOf(0.80, 0.20))
            .gap(ColumnsComponent.Gap.NONE)
            .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
            .use()

        Jt.html(
            """
            <div style="display:flex;align-items:center;gap:18px;padding:6px 0;">
              <span style="color:#1a3a6b;font-size:0.88em;font-weight:500;">
                ← Back to Chat
              </span>
              <span style="font-size:1.35em;font-weight:700;color:#1a1a2e;
                  border-bottom:2px solid #1a3a6b;padding-bottom:2px;">
                Enrichir
              </span>
            </div>
        """.trimIndent()
        ).use(headerBar.col(0))

        val cancelHeaderForm = Jt.form().key("cancel-header-form").use(headerBar.col(1))
        Jt.formSubmitButton("← Back to Chat").use(cancelHeaderForm)

        Jt.markdown("---").use()

        // ── Bloc intro "Optimisation du Prompt" ───────────────────────────────────
        Jt.html(
            """
            <div style="background:#f8f9ff;border:1px solid #dde3f5;border-radius:8px;
                padding:16px 20px;margin:0 0 18px 0;">
              <div style="font-size:1.15em;font-weight:700;color:#1a1a2e;margin-bottom:5px;">
                Optimisation du Prompt
              </div>
              <div style="font-size:0.87em;color:#666;line-height:1.5;">
                Affinage de l'intelligence contextuelle pour des résultats de haute
                précision via le moteur Enrichir AI.
              </div>
            </div>
        """.trimIndent()
        ).use()

        // ── Deux colonnes : Language Model | Select Resources ────────────────────
        val allProviders = listOf(
            "anthropic", "gemini", "huggingface", "mistral", "ollama", "grok", "groq"
        )
        val currentEnrichProvider = state.getString("enrichProvider")
            ?.takeIf { it.isNotBlank() } ?: "ollama"
        val providerIndex = allProviders.indexOf(currentEnrichProvider)
            .takeIf { it >= 0 } ?: 4

        val enrichModels = availableModels(cfg, currentEnrichProvider)
            .ifEmpty { listOf("$currentEnrichProvider-default") }
        val currentEnrichModel = state.getString("enrichModel")
            ?.takeIf { it.isNotBlank() } ?: enrichModels.first()
        val enrichModelIndex = enrichModels.indexOf(currentEnrichModel)
            .takeIf { it >= 0 } ?: 0

        val twoColBar = Jt.columns(2)
            .widths(listOf(0.48, 0.52))
            .gap(ColumnsComponent.Gap.MEDIUM)
            .use()

        // ── col(0) Language Model ─────────────────────────────────────────────────
        Jt.html(
            """
            <div style="font-size:0.85em;font-weight:600;color:#1a3a6b;
                margin-bottom:8px;display:flex;align-items:center;gap:6px;">
              🌐 Language Model
            </div>
        """.trimIndent()
        ).use(twoColBar.col(0))

        val pickedProvider = Jt.selectbox("Provider", allProviders)
            .index(providerIndex)
            .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
            .width("stretch")
            .use(twoColBar.col(0))

        if (pickedProvider != currentEnrichProvider) {
            state["enrichProvider"] = pickedProvider
            state["enrichModel"] = ""
            Jt.rerun()
        }

        val pickedEnrichModel = Jt.selectbox("Modèle", enrichModels)
            .index(enrichModelIndex)
            .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
            .width("stretch")
            .use(twoColBar.col(0))

        if (pickedEnrichModel != currentEnrichModel) {
            state["enrichModel"] = pickedEnrichModel
            Jt.rerun()
        }

        Jt.html(
            """
            <div style="font-size:0.79em;color:#9e9e9e;margin-top:6px;line-height:1.4;">
              Selected model handles complex reasoning and creative generation.
            </div>
        """.trimIndent()
        ).use(twoColBar.col(0))

        // ── col(1) Select Resources (embeds RAG) ──────────────────────────────────
        val embedsCfg = with(embedsYmlConfig) { projectDir.loadEmbedsConfiguration() }
        val embedNames = embedsCfg.embeds.map { it.name }

        @Suppress("UNCHECKED_CAST")
        val selectedEmbeds = state["enrichEmbeds"] as? MutableList<String>
            ?: mutableListOf<String>().also { state["enrichEmbeds"] = it }

        Jt.html(
            """
            <div style="font-size:0.85em;font-weight:600;color:#1a3a6b;
                margin-bottom:8px;display:flex;align-items:center;gap:6px;">
              📂 Select Resources
            </div>
        """.trimIndent()
        ).use(twoColBar.col(1))

        // Tags pills des embeds sélectionnés
        if (selectedEmbeds.isNotEmpty()) {
            val tagsHtml = selectedEmbeds.joinToString("") { name ->
                val embed = embedsCfg.embeds.firstOrNull { it.name == name }
                val pathInfo = embed?.path ?: "?"
                """<span style="display:inline-flex;align-items:center;gap:4px;
                    background:#f0f4ff;border:1px solid #c5cae9;border-radius:4px;
                    padding:2px 8px;margin:2px 2px;font-size:0.81em;color:#1a3a6b;">
                  📄 $pathInfo
                </span>"""
            }
            Jt.html("""<div style="margin-bottom:6px;flex-wrap:wrap;">$tagsHtml</div>""")
                .use(twoColBar.col(1))
        }

        if (embedNames.isEmpty()) {
            Jt.info("Aucun embed — créez embeds.yml à la racine.").use(twoColBar.col(1))
        } else {
            val embedIndex = selectedEmbeds
                .mapNotNull { name -> embedNames.indexOf(name).takeIf { it >= 0 } }
                .firstOrNull() ?: 0

            val pickedEmbed = Jt.selectbox("Embed RAG", embedNames)
                .index(embedIndex)
                .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
                .width("stretch")
                .use(twoColBar.col(1))

            val addEmbedForm = Jt.form().key("add-embed-form").use(twoColBar.col(1))
            Jt.formSubmitButton("＋ Attach File").use(addEmbedForm)

            if (Jt.componentsState()["add-embed-form"] == true) {
                if (pickedEmbed !in selectedEmbeds) {
                    selectedEmbeds.add(pickedEmbed); Jt.rerun()
                }
            }

            selectedEmbeds.toList().forEachIndexed { i, name ->
                val removeForm = Jt.form().key("remove-embed-$i").use(twoColBar.col(1))
                Jt.formSubmitButton("✕ $name").use(removeForm)
                if (Jt.componentsState().get("remove-embed-$i") == true) {
                    selectedEmbeds.remove(name); Jt.rerun()
                }
            }
        }

        Jt.markdown("---").use()

        // ── Prompt Editor ─────────────────────────────────────────────────────────
        //
        // Header avec toolbar B/I/<>/🔗 simulée en HTML statique + compteur mots.
        // Contexte injecté visible uniquement si enrichedPrompt ≠ rawPrompt.
        //
        val wordCount = enrichedPrompt.trim()
            .let { if (it.isBlank()) 0 else it.split(Regex("\\s+")).size }

        Jt.html(
            """
            <div style="background:#fff;border:1px solid #dde3f5;
                border-radius:8px 8px 0 0;padding:10px 16px;
                display:flex;align-items:center;justify-content:space-between;">
              <div style="display:flex;align-items:center;gap:8px;
                  font-weight:600;font-size:0.92em;color:#1a1a2e;">
                <span style="color:#e65100;font-size:1.05em;">≡⚡</span>
                Prompt Editor
              </div>
              <div style="display:flex;align-items:center;gap:12px;">
                <span style="display:flex;gap:10px;color:#555;font-size:0.93em;
                    align-items:center;">
                  <strong style="cursor:default;">B</strong>
                  <em style="cursor:default;">I</em>
                  <span style="cursor:default;font-weight:700;">•≡</span>
                  <span style="font-family:monospace;cursor:default;">&lt;/&gt;</span>
                  <span style="cursor:default;">🔗</span>
                </span>
                <span style="font-size:0.79em;color:#9e9e9e;">
                  Words: <strong>$wordCount</strong>
                </span>
              </div>
            </div>
        """.trimIndent()
        ).use()

        // Bloc contexte injecté (fond orange, style maquette)
        if (enrichedPrompt.isNotBlank() && enrichedPrompt != rawPrompt) {
            val injected = enrichedPrompt.removePrefix(rawPrompt).trim()
            if (injected.isNotBlank()) {
                Jt.html(
                    """
                    <div style="background:#fff3e0;border-left:4px solid #e65100;
                        padding:9px 14px;font-size:0.86em;color:#5d3a00;
                        font-style:italic;border-radius:0;">
                      [CONTEXTE_AUTOMATIQUE] ${injected.replace("\n", "<br/>")}
                    </div>
                """.trimIndent()
                ).use()
            }
        }

        Jt.textArea("Prompt enrichi")
            .value(enrichedPrompt)
            .height(220)
            .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
            .onChange { state["enrichedPrompt"] = it }
            .use()

        Jt.markdown("---").use()

        // ── Actions ───────────────────────────────────────────────────────────────
        val actionsBar = Jt.columns(3)
            .widths(listOf(0.42, 0.32, 0.26))
            .gap(ColumnsComponent.Gap.SMALL)
            .use()

        val sendForm = Jt.form().key("enrich-send-form").use(actionsBar.col(0))
        Jt.formSubmitButton("⚡ Enrichir AI").use(sendForm)

        val reenrichForm = Jt.form().key("enrich-reenrich-form").use(actionsBar.col(1))
        Jt.formSubmitButton("♻️ Re-enrichir").use(reenrichForm)

        val cancelForm = Jt.form().key("enrich-cancel-form").use(actionsBar.col(2))
        Jt.formSubmitButton("❌ Annuler").use(cancelForm)

        val actionCs = Jt.componentsState()

        if (actionCs.get("enrich-send-form") == true) {
            val finalPrompt = state.getString("enrichedPrompt") ?: rawPrompt
            history.add(
                Triple(
                    "⚡",
                    "Prompt enrichi → $currentEnrichModel (${finalPrompt.length} car.)",
                    "ollama"
                )
            )
            history.add(Triple("⏳", "En attente de Claude...", "wait"))
            state["playwrightStatus"] = "waiting"
            state["page"] = "chat"
            // TODO : envoyer finalPrompt via activePage (étape Playwright)
            Jt.rerun()
        }

        if (actionCs["enrich-reenrich-form"] == true) {
            val mocked = "[$currentEnrichModel + ${selectedEmbeds.size} embed(s)] $rawPrompt"
            state["enrichedPrompt"] = mocked
            Jt.rerun()
        }

        val cancelPressed = actionCs["enrich-cancel-form"] == true
                || actionCs["cancel-header-form"] == true
        if (cancelPressed) {
            if (history.isNotEmpty()) history.removeLastOrNull()
            state["enrichedPrompt"] = ""
            state["enrichRawPrompt"] = ""
            state["page"] = "chat"
            Jt.rerun()
        }

        return
    }

    // ════════════════════════════════════════════════════════════════════════
    // VUE "chat"
    // ════════════════════════════════════════════════════════════════════════

    val searchOpen = state["searchOpen"] as? Boolean ?: false
    val searchTerm = state.getString("searchTerm") ?: ""
    val searchIndex = (state["searchIndex"] as? Int) ?: 0

    // ── Header ────────────────────────────────────────────────────────────────
    val headerBar = Jt.columns(2)
        .widths(listOf(0.85, 0.15))
        .gap(ColumnsComponent.Gap.NONE)
        .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
        .use()

    Jt.html(
        """
        <div style="display:flex;align-items:center;gap:12px;padding:6px 0;">
          <span style="font-size:1.5em;font-weight:700;color:#1a1a2e;">Chat</span>
          <span style="background:#e8f0fe;border-radius:12px;padding:3px 12px;
              font-size:0.82em;font-weight:600;color:#1a3a6b;white-space:nowrap;">
            ● $currentModel
          </span>
        </div>
    """.trimIndent()
    ).use(headerBar.col(0))

    val searchClicked = Jt.button("🔍").key("search-toggle").use(headerBar.col(1))
    if (searchClicked) {
        state["searchOpen"] = !searchOpen
        Jt.rerun()
    }

    Jt.markdown("---").use()

    // ── Bloc recherche — conditionnel ─────────────────────────────────────────
    val searchContainer = Jt.container().key("search-container").use()
    if (searchOpen) {
        val searchForm = Jt.form().key("search-form").use(searchContainer)
        val searchInputBar = Jt.columns(2)
            .widths(listOf(0.88, 0.12))
            .gap(ColumnsComponent.Gap.SMALL)
            .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
            .use(searchForm)

        val typedTerm = Jt.textInput("Rechercher")
            .placeholder("Terme de recherche…")
            .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
            .value(searchTerm)
            .use(searchInputBar.col(0))

        Jt.formSubmitButton("🔍").use(searchInputBar.col(1))

        if (Jt.componentsState()["search-form"] == true) {
            state["searchTerm"] = typedTerm.trim()
            state["searchIndex"] = 0
            Jt.rerun()
        }

        if (searchTerm.isNotBlank()) {
            data class Hit(val msgIndex: Int, val role: String, val content: String)

            val hits = history.mapIndexedNotNull { i, (role, content, _) ->
                if (content.contains(searchTerm, ignoreCase = true))
                    Hit(i, role, content) else null
            }
            val total = hits.size
            val safeIndex = if (total == 0) 0 else searchIndex.coerceIn(0, total - 1)

            if (total == 0) {
                Jt.warning("Aucune occurrence pour « $searchTerm »").use(searchContainer)
            } else {
                val navForm = Jt.form().key("search-nav-form").use(searchContainer)
                val navBar = Jt.columns(3)
                    .widths(listOf(0.12, 0.76, 0.12))
                    .gap(ColumnsComponent.Gap.NONE)
                    .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
                    .use(navForm)

                Jt.formSubmitButton("◀").key("search-prev").use(navBar.col(0))
                Jt.markdown("${safeIndex + 1} / $total  · « **$searchTerm** »")
                    .use(navBar.col(1))
                Jt.formSubmitButton("▶").key("search-next").use(navBar.col(2))

                val navCs = Jt.componentsState()
                if (navCs["search-prev"] == true) {
                    state["searchIndex"] = (safeIndex - 1 + total) % total; Jt.rerun()
                }
                if (navCs["search-next"] == true) {
                    state["searchIndex"] = (safeIndex + 1) % total; Jt.rerun()
                }

                Jt.markdown("---").use(searchContainer)

                hits.forEachIndexed { hitIdx, hit ->
                    val isCurrent = hitIdx == safeIndex
                    val bgColor = if (hit.role == "👤") "#e8f4fd" else "#f8f9ff"
                    val border = if (isCurrent) "2px solid #FF9800" else "1px solid #ddd"
                    val shadow = if (isCurrent) "box-shadow:0 0 0 2px #FF980044;" else ""
                    val highlighted = hit.content.replace(
                        Regex("(?i)(${Regex.escape(searchTerm)})"),
                        "<mark style=\"background:#FFF176;border-radius:2px;\">$1</mark>"
                    )
                    Jt.html(
                        """
                        <div style="background:$bgColor;border:$border;border-radius:6px;
                            padding:8px 12px;margin:4px 0;font-size:0.91em;$shadow">
                          <strong>${hit.role}</strong>
                          ${if (isCurrent) " <span style=\"color:#FF9800;font-size:0.83em;\">◀ occ.${hitIdx + 1}</span>" else ""}
                          <br/>${highlighted.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use(searchContainer)
                }
            }
        }
        Jt.markdown("---").use()
    }

    // ── Historique ────────────────────────────────────────────────────────────
    if (history.isEmpty()) {
        Jt.html(
            """
            <div style="text-align:center;color:#bdbdbd;padding:56px 0 40px 0;
                font-size:0.97em;">
              Démarrez la conversation…
            </div>
        """.trimIndent()
        ).use()
    } else {
        history.forEachIndexed { i, (_, content, type) ->
            when (type) {
                "user" -> {
                    Jt.html(
                        """
                        <div style="display:flex;justify-content:flex-end;margin:6px 0;">
                          <div style="background:#ffffff;border-left:4px solid #1a3a6b;
                              border-radius:6px;padding:10px 14px;max-width:82%;
                              font-size:0.94em;
                              box-shadow:0 1px 3px rgba(0,0,0,0.07);
                              color:#1a1a2e;">
                            ${content.replace("\n", "<br/>")}
                          </div>
                        </div>
                    """.trimIndent()
                    ).use()
                }

                "wait" -> {
                    Jt.html(
                        """
                        <div style="background:#fff8e1;border-left:4px solid #FF9800;
                            border-radius:6px;padding:10px 14px;margin:6px 0;
                            font-size:0.94em;color:#7c6400;">
                          ⏳ ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use()
                }

                "tool" -> {
                    Jt.html(
                        """
                        <div style="background:#fce4ec;border-left:4px solid #E91E63;
                            border-radius:6px;padding:10px 14px;margin:6px 0;
                            font-size:0.93em;color:#880e4f;">
                          <strong>🔧 Tool</strong><br/>
                          ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use()
                }

                "system" -> {
                    Jt.html(
                        """
                        <div style="background:#f5f5f5;border-left:4px solid #9E9E9E;
                            border-radius:6px;padding:8px 14px;margin:6px 0;
                            font-size:0.88em;color:#616161;">
                          ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use()
                }

                else -> {
                    // "assistant" | "ollama"
                    val msgBar = Jt.columns(2)
                        .widths(listOf(0.86, 0.14))
                        .gap(ColumnsComponent.Gap.SMALL)
                        .verticalAlignment(ColumnsComponent.VerticalAlignment.TOP)
                        .use()

                    Jt.html(
                        """
                        <div style="background:#f8f9ff;border-left:4px solid #1a3a6b;
                            border-radius:6px;padding:14px 16px;margin:4px 0;
                            font-size:0.94em;color:#1a1a2e;">
                          <div style="display:flex;align-items:center;gap:6px;
                              margin-bottom:8px;color:#1a3a6b;font-size:0.78em;
                              font-weight:700;text-transform:uppercase;
                              letter-spacing:0.06em;">
                            <span>✦</span> ASSISTANT INSIGHT
                          </div>
                          ${content.replace("\n", "<br/>")}
                        </div>
                    """.trimIndent()
                    ).use(msgBar.col(0))

                    val enrichMsgForm = Jt.form().key("enrich-msg-$i").use(msgBar.col(1))
                    Jt.formSubmitButton("⚡ Enrich").use(enrichMsgForm)

                    if (Jt.componentsState()["enrich-msg-$i"] == true) {
                        state["enrichRawPrompt"] = content
                        state["enrichedPrompt"] = content
                        state["page"] = "enrich"
                        Jt.rerun()
                    }
                }
            }
        }
    }

    Jt.markdown("---").use()

    // ── Bloc tool call — conditionnel ─────────────────────────────────────────
    val pendingTool = state.getString("pendingToolCall") ?: ""
    if (pendingTool.isNotBlank()) {
        Jt.markdown("🔧 **Tool call détecté** : `$pendingTool`").use()
        Jt.textInput("Arguments")
            .value(state.getString("toolCallArgs") ?: "")
            .onChange { state["toolCallArgs"] = it }
            .use()

        val toolBar = Jt.columns(2)
            .widths(listOf(0.50, 0.50))
            .gap(ColumnsComponent.Gap.SMALL)
            .use()

        val toolAllowForm = Jt.form().key("tool-allow-form").use(toolBar.col(0))
        Jt.formSubmitButton("✅ Autoriser").use(toolAllowForm)
        val toolDenyForm = Jt.form().key("tool-deny-form").use(toolBar.col(1))
        Jt.formSubmitButton("❌ Refuser").use(toolDenyForm)

        val toolCs = Jt.componentsState()
        if (toolCs["tool-allow-form"] == true) {
            state["pendingToolCall"] = ""
            Jt.rerun()
        }
        if (toolCs["tool-deny-form"] == true) {
            history.add(Triple("🔧", "Tool `$pendingTool` refusé.", "system"))
            state["pendingToolCall"] = ""
            Jt.rerun()
        }
        Jt.markdown("---").use()
    }

    // ── Barre de saisie ───────────────────────────────────────────────────────
    //
    // ARCHITECTURE : un seul form "input-form" englobe les deux boutons ET le
    // textArea. Deux formSubmitButton avec clés distinctes ("enrich-submit" /
    // "direct-submit") permettent de discriminer l'action dans componentsState.
    //
    // Raison : textArea hors form → onChange non garanti au même cycle que le
    // submit → pendingMessage lu dans les handlers = valeur N-1 (cycle précédent).
    // Avec textArea dans le form → use() retourne la valeur courante (String) ✅
    //
    val inputGroup = Jt.container().key("input-group").use()

    val wordCount = (state["wordCount"] as? Int) ?: 0

    // ── Ligne 1 : expander modèle ─────────────────────────────────────────────
    val modelExpander = Jt.expander("$currentModel ▾")
        .expanded(false)
        .use(inputGroup)
    Jt.markdown("#### Modèles disponibles").use(modelExpander)
    Jt.markdown("---").use(modelExpander)
    val pickedModel = Jt.radio("Modèle", models)
        .index(currentIndex)
        .labelVisibility(JtComponent.LabelVisibility.COLLAPSED)
        .use(modelExpander)
    if (pickedModel != currentModel) {
        state["selectedModel"] = pickedModel; Jt.rerun()
    }
    Jt.markdown("---").use(modelExpander)
    Jt.button("➕ Ajouter un provider / modèle").use(modelExpander)

    // ── Lignes 2 + 3 : form unique englobant boutons + textArea ──────────────
    val inputForm = Jt.form().key("input-form").use(inputGroup)

    val btnBar = Jt.columns(3)
        .widths(listOf(0.15, 0.55, 0.30))
        .gap(ColumnsComponent.Gap.SMALL)
        .verticalAlignment(ColumnsComponent.VerticalAlignment.CENTER)
        .use(inputForm)

    // col(0) — 📁 attach statique (pas encore fonctionnel)
    Jt.html(
        """
        <div style="display:flex;align-items:center;justify-content:center;
            padding:4px 0;opacity:0.40;cursor:not-allowed;font-size:1.1em;"
             title="Attach (bientôt disponible)">📁</div>
    """.trimIndent()
    ).use(btnBar.col(0))

    // col(1) — ⚡ Enrich
    Jt.formSubmitButton("⚡ Enrich")
        .key("enrich-submit")
        .disabled(busy)
        .use(btnBar.col(1))

    // col(2) — ⬆️ Send direct
    Jt.formSubmitButton("⬆️")
        .key("direct-submit")
        .disabled(busy)
        .use(btnBar.col(2))

    // Compteur mots
    if (wordCount > 0) {
        Jt.html(
            """
            <span style="font-size:0.78em;color:#9e9e9e;padding:2px 0;
                display:block;">Mots : <strong>$wordCount</strong></span>
        """.trimIndent()
        ).use(inputForm)
    }

    // textArea dans le form → use() retourne String du cycle courant ✅
    val typedMessage: String = Jt.textArea("Message")
        .placeholder(if (busy) "En attente de la réponse…" else "Type your message here…")
        .labelVisibility(JtComponent.LabelVisibility.HIDDEN)
        .height(120)
        .disabled(busy)
        .value(state.getString("pendingMessage") ?: "")
        .onChange { txt ->
            state["pendingMessage"] = txt
            state["wordCount"] = if (txt.isBlank()) 0 else txt.trim().split(Regex("\\s+")).size
        }
        .use(inputForm)

    // ── Handlers ──────────────────────────────────────────────────────────────
    val inputCs = Jt.componentsState()

    if (inputCs["enrich-submit"] == true) {
        val pending = typedMessage.trim()
        println("[DEBUG enrich-submit] fired — pending='$pending'")
        if (pending.isNotBlank()) history.add(Triple("👤", pending, "user"))
        state["enrichRawPrompt"] = pending
        state["enrichedPrompt"] = pending
        state["pendingMessage"] = ""
        state["wordCount"] = 0
        state["page"] = "enrich"
        Jt.rerun()
    }

    if (inputCs["direct-submit"] == true) {
        val pending = typedMessage.trim()
        println("[DEBUG direct-submit] fired — pending='$pending'")
        if (pending.isNotBlank()) {
            history.add(Triple("👤", pending, "user"))
            history.add(Triple("⏳", "En attente de Claude...", "wait"))
            state["playwrightStatus"] = "waiting"
            state["pendingMessage"] = ""
            state["wordCount"] = 0
            // TODO : envoyer via activePage (étape Playwright)
            Jt.rerun()
        }
    }
}