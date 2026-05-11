import codebase.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import readme.*
import site.*
import slider.*
import snapshot.SnapshotManager

val runtime = project.extensions.getByType(JavaPluginExtension::class.java)
    .sourceSets.getByName("main").runtimeClasspath

project.tasks.register("verifyReadMeToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that ReadmePlantUmlConfig anonymization masks sensitive fields"
    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anon = ReadmeYmlAnonymizer()
        val yamlReal = with(anon) {
            ReadmePlantUmlConfig(
                source = SourceConfig(dir = "src", defaultLang = "fr"),
                git = GitConfig(token = "ghp_supersecret123", userName = "my-bot",
                    userEmail = "my-bot@company.com", watchedBranches = listOf("main", "develop"))
            ).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 1: real token ──────────────────────────────")
        logger.lifecycle(yamlReal)
        check(anon.TOKEN_MASK in yamlReal) { "FAIL case 1" }
        check("ghp_supersecret123" !in yamlReal) { "FAIL case 1" }
        check(anon.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1" }
        check("my-bot" !in yamlReal) { "FAIL case 1" }
        check(anon.anonymizedEmail() in yamlReal) { "FAIL case 1" }
        check("my-bot@company.com" !in yamlReal) { "FAIL case 1" }
        check("fr" in yamlReal) { "FAIL case 1" }
        check("src" in yamlReal) { "FAIL case 1" }
        check("develop" in yamlReal) { "FAIL case 1" }
        logger.lifecycle("✅ case 1 OK")

        val yamlPlaceholder = with(anon) {
            ReadmePlantUmlConfig(git = GitConfig(token = "<YOUR_GITHUB_PAT>")).toAnonymizedYaml(mapper)
        }
        check(anon.TOKEN_MASK in yamlPlaceholder) { "FAIL case 2" }
        check("<YOUR_GITHUB_PAT>" !in yamlPlaceholder) { "FAIL case 2" }
        check(anon.anonymizedEmail() in yamlPlaceholder) { "FAIL case 2" }
        logger.lifecycle("✅ case 2 OK")

        val yamlEmpty = with(anon) { ReadmePlantUmlConfig(git = GitConfig(token = "")).toAnonymizedYaml(mapper) }
        check(anon.TOKEN_MASK in yamlEmpty) { "FAIL case 3" }
        check(anon.anonymizedEmail() in yamlEmpty) { "FAIL case 3" }
        logger.lifecycle("✅ case 3 OK")

        val cfgIdem = ReadmePlantUmlConfig(git = GitConfig(token = "ghp_idempotence", userName = "real-user"))
        with(anon) { cfgIdem.toAnonymizedYaml(mapper) }
        val yaml2 = with(anon) { cfgIdem.toAnonymizedYaml(mapper) }
        check("ghp_idempotence" !in yaml2) { "FAIL case 4" }
        check("real-user" !in yaml2) { "FAIL case 4" }
        check(anon.TOKEN_MASK in yaml2) { "FAIL case 4" }
        check(anon.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4" }
        logger.lifecycle("✅ case 4 OK")

        val mgr = SnapshotManager()
        with(mgr) {
            check(GitConfig(token = "ghp_valid").resolvedToken() == "ghp_valid") { "FAIL case 5" }
            logger.lifecycle("✅ case 5 OK")
            try { GitConfig(token = "").resolvedToken(); error("FAIL case 6") }
            catch (_: IllegalStateException) { logger.lifecycle("✅ case 6 OK") }
            try { GitConfig(token = "<YOUR_GITHUB_PAT>").resolvedToken(); error("FAIL case 7") }
            catch (_: IllegalStateException) { logger.lifecycle("✅ case 7 OK") }
        }
        logger.lifecycle("✅ verifyReadMeToAnonymizedYaml — all cases passed")
    }
}

project.tasks.register("verifySliderToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that SliderConfiguration anonymization masks sensitive fields"
    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anon = SliderYmlAnonymizer()
        val yamlReal = with(anon) {
            SliderConfiguration(
                srcPath = "docs/asciidocRevealJs",
                pushSlider = GitPushConfiguration(from = "/docs/asciidocRevealJs", to = "cvs",
                    branch = "production", message = "slides show",
                    repo = RepositoryConfiguration(name = "slider-gradle",
                        repository = "https://github.com/foo/bar.git",
                        credentials = RepositoryCredentials(username = "cheroliv",
                            password = "ghp_EkAxg8TgUFBT2ihx8QH6vn0I6k4T"))),
                ai = AiConfiguration(gemini = listOf("FAKE-gemini-key-for-test-only"),
                    huggingface = listOf("FAKE-hf-key-for-test-only"),
                    mistral = listOf("FAKE-mistral-key-1", "FAKE-mistral-key-2"))
            ).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 1: real credentials ────────────────────────")
        logger.lifecycle(yamlReal)
        check("ghp_EkAxg8TgUFBT2ihx8QH6vn0I6k4T" !in yamlReal) { "FAIL case 1" }
        check("cheroliv" !in yamlReal) { "FAIL case 1" }
        check("https://github.com/foo/bar.git" !in yamlReal) { "FAIL case 1" }
        check("production" !in yamlReal) { "FAIL case 1" }
        check("FAKE-gemini-key-for-test-only" !in yamlReal) { "FAIL case 1" }
        check("FAKE-hf-key-for-test-only" !in yamlReal) { "FAIL case 1" }
        check("FAKE-mistral-key-1" !in yamlReal) { "FAIL case 1" }
        check("FAKE-mistral-key-2" !in yamlReal) { "FAIL case 1" }
        check(anon.TOKEN_MASK in yamlReal) { "FAIL case 1" }
        check(anon.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1" }
        check(anon.REPO_MASK in yamlReal) { "FAIL case 1" }
        check(anon.BRANCH_MASK in yamlReal) { "FAIL case 1" }
        check("docs/asciidocRevealJs" in yamlReal) { "FAIL case 1" }
        check("slides show" in yamlReal) { "FAIL case 1" }
        check("slider-gradle" in yamlReal) { "FAIL case 1" }
        logger.lifecycle("✅ case 1 OK")

        val yamlEmptyAi = with(anon) {
            SliderConfiguration(
                pushSlider = GitPushConfiguration(from = "", to = "", branch = "feature", message = "",
                    repo = RepositoryConfiguration(name = "",
                        repository = "https://github.com/real/repo.git",
                        credentials = RepositoryCredentials(username = "cheroliv-bot",
                            password = "ghp_emptyAiSecret99"))),
                ai = AiConfiguration()
            ).toAnonymizedYaml(mapper)
        }
        check("https://github.com/real/repo.git" !in yamlEmptyAi) { "FAIL case 2" }
        check("cheroliv-bot" !in yamlEmptyAi) { "FAIL case 2" }
        check("ghp_emptyAiSecret99" !in yamlEmptyAi) { "FAIL case 2" }
        check(anon.REPO_MASK in yamlEmptyAi) { "FAIL case 2" }
        logger.lifecycle("✅ case 2 OK")

        val yamlNulls = with(anon) { SliderConfiguration(srcPath = "src/slides").toAnonymizedYaml(mapper) }
        check("src/slides" in yamlNulls) { "FAIL case 3" }
        logger.lifecycle("✅ case 3 OK")

        val cfgIdem = SliderConfiguration(
            pushSlider = GitPushConfiguration(from = "", to = "", branch = "prod", message = "",
                repo = RepositoryConfiguration(name = "",
                    repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "realuser", password = "realpass"))),
            ai = AiConfiguration(gemini = listOf("real-gemini-key")))
        with(anon) { cfgIdem.toAnonymizedYaml(mapper) }
        val yaml2 = with(anon) { cfgIdem.toAnonymizedYaml(mapper) }
        check("realuser" !in yaml2) { "FAIL case 4" }
        check("realpass" !in yaml2) { "FAIL case 4" }
        check("real-gemini-key" !in yaml2) { "FAIL case 4" }
        check(anon.TOKEN_MASK in yaml2) { "FAIL case 4" }
        check(anon.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4" }
        logger.lifecycle("✅ case 4 OK")

        val yamlMulti = with(anon) {
            SliderConfiguration(ai = AiConfiguration(gemini = listOf("key1", "key2", "key3"),
                huggingface = listOf("hf1", "hf2"), mistral = listOf("ms1"))).toAnonymizedYaml(mapper)
        }
        listOf("key1", "key2", "key3", "hf1", "hf2", "ms1").forEach { k ->
            check(k !in yamlMulti) { "FAIL case 5: '$k' is visible!" }
        }
        val maskCount = yamlMulti.split(anon.TOKEN_MASK).size - 1
        check(maskCount == 6) { "FAIL case 5: expected 6 masked keys, got $maskCount" }
        logger.lifecycle("✅ case 5 OK: $maskCount AI keys masked")
        logger.lifecycle("✅ verifySliderToAnonymizedYaml — all cases passed")
    }
}

project.tasks.register("verifySiteToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that SiteConfiguration anonymization masks sensitive fields"
    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anon = SiteYmlAnonymizer()
        val yamlReal = with(anon) {
            SiteConfiguration(
                bake = BakeConfiguration(srcPath = "site", destDirPath = "bake"),
                pushPage = GitPushConfiguration(from = "bake", to = "cvs", branch = "production",
                    message = "com.cheroliv.bakery",
                    repo = RepositoryConfiguration(name = "pages-content/bakery",
                        repository = "https://github.com/foo/bar.git",
                        credentials = RepositoryCredentials(username = "foo",
                            password = "ghoS9WycCyJ8pX24Ge9l"))),
                pushMaquette = GitPushConfiguration(from = "maquette", to = "cvs", branch = "production",
                    message = "cheroliv-maquette",
                    repo = RepositoryConfiguration(name = "bakery-maquette",
                        repository = "https://github.com/baz/qux.git",
                        credentials = RepositoryCredentials(username = "foo",
                            password = "ghoFRVZTNo8OycCyJ8pX24Ge9l")))
            ).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 1: pushPage + pushMaquette ─────────────────")
        logger.lifecycle(yamlReal)
        check("ghoS9WycCyJ8pX24Ge9l" !in yamlReal) { "FAIL case 1" }
        check("ghoFRVZTNo8OycCyJ8pX24Ge9l" !in yamlReal) { "FAIL case 1" }
        check("foo" !in yamlReal) { "FAIL case 1" }
        check("https://github.com/foo/bar.git" !in yamlReal) { "FAIL case 1" }
        check("https://github.com/baz/qux.git" !in yamlReal) { "FAIL case 1" }
        check(anon.TOKEN_MASK in yamlReal) { "FAIL case 1" }
        check(anon.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1" }
        check(anon.REPO_MASK in yamlReal) { "FAIL case 1" }
        check(anon.BRANCH_MASK in yamlReal) { "FAIL case 1" }
        check("site" in yamlReal) { "FAIL case 1" }
        check("bake" in yamlReal) { "FAIL case 1" }
        check("com.cheroliv.bakery" in yamlReal) { "FAIL case 1" }
        check("cheroliv-maquette" in yamlReal) { "FAIL case 1" }
        check("pages-content/bakery" in yamlReal) { "FAIL case 1" }
        check("bakery-maquette" in yamlReal) { "FAIL case 1" }
        logger.lifecycle("✅ case 1 OK")

        val yamlSupabase = with(anon) {
            SiteConfiguration(pushPage = GitPushConfiguration(), pushMaquette = GitPushConfiguration(),
                supabase = SupabaseContactFormConfig(
                    project = SupabaseProjectInfo(url = "https://hkgrvjgukx.supabase.co",
                        publicKey = "eyJhbCI6IkpXVCJ9..."),
                    schema = SupabaseDatabaseSchema(
                        contacts = SupabaseTable("public.contacts", listOf(
                            SupabaseColumn("id", "uuid"), SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("name", "text"), SupabaseColumn("email", "text"),
                            SupabaseColumn("telephone", "text")), rlsEnabled = true),
                        messages = SupabaseTable("public.messages", listOf(
                            SupabaseColumn("id", "uuid"), SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("contact_id", "uuid"), SupabaseColumn("subject", "text"),
                            SupabaseColumn("message", "text")), rlsEnabled = true)),
                    rpc = SupabaseRpcFunction("public.handle_contact_form",
                        listOf(SupabaseParam("p_name", "text"), SupabaseParam("p_email", "text"),
                            SupabaseParam("p_subject", "text"), SupabaseParam("p_message", "text"))))
            ).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 2: supabase url + publicKey ────────────────")
        logger.lifecycle(yamlSupabase)
        check("https://hkgrvjgukx.supabase.co" !in yamlSupabase) { "FAIL case 2" }
        check(anon.URL_MASK in yamlSupabase) { "FAIL case 2" }
        check(anon.TOKEN_MASK in yamlSupabase) { "FAIL case 2" }
        check("public.contacts" in yamlSupabase) { "FAIL case 2" }
        check("public.messages" in yamlSupabase) { "FAIL case 2" }
        check("public.handle_contact_form" in yamlSupabase) { "FAIL case 2" }
        logger.lifecycle("✅ case 2 OK")

        val yamlNullPush = with(anon) {
            SiteConfiguration(pushPage = GitPushConfiguration(), pushMaquette = GitPushConfiguration(),
                pushSource = null, pushTemplate = null).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 3: pushSource + pushTemplate nulls ─────────")
        logger.lifecycle(yamlNullPush)
        logger.lifecycle("✅ case 3 OK")

        val yamlAllPush = with(anon) {
            SiteConfiguration(pushPage = GitPushConfiguration(), pushMaquette = GitPushConfiguration(),
                pushSource = GitPushConfiguration(branch = "secret-branch",
                    repo = RepositoryConfiguration(repository = "https://github.com/secret/source.git",
                        credentials = RepositoryCredentials(username = "src-user", password = "src-pass"))),
                pushTemplate = GitPushConfiguration(branch = "template-branch",
                    repo = RepositoryConfiguration(repository = "https://github.com/secret/template.git",
                        credentials = RepositoryCredentials(username = "tmpl-user", password = "tmpl-pass")))
            ).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 4: pushSource + pushTemplate non-nulls ──────")
        logger.lifecycle(yamlAllPush)
        check("https://github.com/secret/source.git" !in yamlAllPush) { "FAIL case 4" }
        check("secret-branch" !in yamlAllPush) { "FAIL case 4" }
        check("src-user" !in yamlAllPush) { "FAIL case 4" }
        check(anon.REPO_MASK in yamlAllPush) { "FAIL case 4" }
        logger.lifecycle("✅ case 4 OK")

        val cfgIdem = SiteConfiguration(
            pushPage = GitPushConfiguration(branch = "prod",
                repo = RepositoryConfiguration(repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "idem-user", password = "idem-pass"))),
            pushMaquette = GitPushConfiguration())
        with(anon) { cfgIdem.toAnonymizedYaml(mapper) }
        val yaml2 = with(anon) { cfgIdem.toAnonymizedYaml(mapper) }
        check("idem-user" !in yaml2) { "FAIL case 5" }
        check("idem-pass" !in yaml2) { "FAIL case 5" }
        check(anon.TOKEN_MASK in yaml2) { "FAIL case 5" }
        logger.lifecycle("✅ case 5 OK")
        logger.lifecycle("✅ verifySiteToAnonymizedYaml — all cases passed")
    }
}

project.tasks.register("verifyCodebaseToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that CodebaseConfiguration anonymization masks API keys"
    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anon = CodebaseYmlAnonymizer()
        val yaml1 = with(anon) {
            CodebaseConfiguration(ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(defaultAccount = "perso", defaultKey = "chatbot",
                    models = listOf("claude-opus-4-5", "claude-sonnet-4-5"),
                    defaultModel = "claude-opus-4-5",
                    accounts = listOf(LlmAccount("perso", "p@gmail.com",
                        listOf(NamedApiKey("chatbot", "sk-ant-api03-supersecret", "2026-12-31")))))
            )).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 1: anthropic real key ──────────────────────")
        logger.lifecycle(yaml1)
        check("sk-ant-api03-supersecret" !in yaml1) { "FAIL case 1" }
        check(anon.TOKEN_MASK in yaml1) { "FAIL case 1" }
        check("perso" in yaml1) { "FAIL case 1" }
        check("p@gmail.com" in yaml1) { "FAIL case 1" }
        check("chatbot" in yaml1) { "FAIL case 1" }
        check("2026-12-31" in yaml1) { "FAIL case 1" }
        check("claude-opus-4-5" in yaml1) { "FAIL case 1" }
        logger.lifecycle("✅ case 1 OK")

        val yaml2 = with(anon) {
            CodebaseConfiguration(ai = AiProvidersConfig(
                gemini = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "g@gmail.com",
                    listOf(NamedApiKey("main", "FAKE-gemini-AIzaSy-test", "2026-12-31"))))),
                huggingface = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "hf@gmail.com",
                    listOf(NamedApiKey("main", "FAKE-hf-token-test", ""))))),
                mistral = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "ms@gmail.com",
                    listOf(NamedApiKey("main", "FAKE-mistral-key-test", "2027-03-01"),
                        NamedApiKey("spare", "mst-spare-key", ""))))),
                grok = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "grok@x.com",
                    listOf(NamedApiKey("main", "FAKE-grok-key-test", ""))))),
                groq = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "groq@groq.com",
                    listOf(NamedApiKey("main", "FAKE-groq-gsk-test", "")))))
            )).toAnonymizedYaml(mapper)
        }
        logger.lifecycle("── case 2: gemini + huggingface + mistral + grok + groq")
        logger.lifecycle(yaml2)
        listOf("FAKE-gemini-AIzaSy-test", "FAKE-hf-token-test", "FAKE-mistral-key-test",
            "FAKE-grok-key-test", "FAKE-groq-gsk-test", "mst-spare-key").forEach {
                check(it !in yaml2) { "FAIL case 2: '$it'" }
            }
        check("g@gmail.com" in yaml2) { "FAIL case 2" }
        check("2026-12-31" in yaml2) { "FAIL case 2" }
        check("spare" in yaml2) { "FAIL case 2" }
        logger.lifecycle("✅ case 2 OK")

        val yaml3 = with(anon) {
            CodebaseConfiguration(ai = AiProvidersConfig(ollama = LlmProviderConfig(
                baseUrl = "http://localhost:11434", models = listOf("llama3.2", "codellama"),
                accounts = listOf(LlmAccount("local", "", emptyList()))
            ))).toAnonymizedYaml(mapper)
        }
        check("http://localhost:11434" in yaml3) { "FAIL case 3" }
        check("llama3.2" in yaml3) { "FAIL case 3" }
        check("codellama" in yaml3) { "FAIL case 3" }
        logger.lifecycle("✅ case 3 OK")

        val yaml4 = with(anon) {
            CodebaseConfiguration(ai = AiProvidersConfig(anthropic = LlmProviderConfig(
                accounts = listOf(LlmAccount("perso", "p@g.com",
                    listOf(NamedApiKey("main", "", ""))))))
            ).toAnonymizedYaml(mapper)
        }
        val maskCount = yaml4.lines().count { anon.TOKEN_MASK in it }
        check(maskCount == 0) { "FAIL case 4: expected 0 masks for blank keys, got $maskCount" }
        logger.lifecycle("✅ case 4 OK")

        val yaml5 = with(anon) {
            CodebaseConfiguration(active = ActiveSelection(provider = "mistral", account = "pro", key = "prod"),
                chatbot = ChatbotConfig(port = 9090, defaultProvider = "mistral")).toAnonymizedYaml(mapper)
        }
        check("mistral" in yaml5) { "FAIL case 5" }
        check("pro" in yaml5) { "FAIL case 5" }
        check("prod" in yaml5) { "FAIL case 5" }
        check("9090" in yaml5) { "FAIL case 5" }
        logger.lifecycle("✅ case 5 OK")

        val cfg6 = CodebaseConfiguration(ai = AiProvidersConfig(
            anthropic = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "p@g.com",
                listOf(NamedApiKey("chatbot", "sk-ant-idempotent", "2027-01-01"))))),
            gemini = LlmProviderConfig(accounts = listOf(LlmAccount("perso", "g@g.com",
                listOf(NamedApiKey("main", "AIza-idempotent", "")))))
        ))
        with(anon) { cfg6.toAnonymizedYaml(mapper) }
        val yaml6b = with(anon) { cfg6.toAnonymizedYaml(mapper) }
        check("sk-ant-idempotent" !in yaml6b) { "FAIL case 6" }
        check("AIza-idempotent" !in yaml6b) { "FAIL case 6" }
        check(anon.TOKEN_MASK in yaml6b) { "FAIL case 6" }
        logger.lifecycle("✅ case 6 OK")

        val localCfg = CodebaseYmlConfig()
        val cfg7 = CodebaseConfiguration(
            active = ActiveSelection(provider = "anthropic", account = "perso", key = "chatbot"),
            ai = AiProvidersConfig(anthropic = LlmProviderConfig(defaultAccount = "perso", defaultKey = "chatbot",
                accounts = listOf(
                    LlmAccount("perso", "p@g.com", listOf(NamedApiKey("chatbot", "sk-ant-chatbot", "2027-06-01"),
                        NamedApiKey("ci", "sk-ant-ci", ""))),
                    LlmAccount("pro", "pro@c.com", listOf(NamedApiKey("prod", "sk-ant-prod", "2025-01-01")))
                ))))
        val resolved = localCfg.resolveActiveKey(cfg7, logger)
        check(resolved?.key == "sk-ant-chatbot") { "FAIL case 7" }
        check(resolved.label == "chatbot") { "FAIL case 7" }
        val cli = localCfg.resolveActiveKey(cfg7, logger, cliProvider = "anthropic", cliAccount = "pro", cliKey = "prod")
        check(cli?.key == "sk-ant-prod") { "FAIL case 7" }
        val cliKey = localCfg.resolveActiveKey(cfg7, logger, cliKey = "ci")
        check(cliKey?.key == "sk-ant-ci") { "FAIL case 7" }
        logger.lifecycle("✅ case 7 OK")
        logger.lifecycle("✅ verifyCodebaseToAnonymizedYaml — all cases passed")
    }
}

project.tasks.register("scaffoldCodebaseYml") {
    group = "codebase"
    description = "Generates codebase.yml scaffold and registers it in .gitignore"
    doLast {
        val projectDir = project.layout.projectDirectory.asFile
        val target = projectDir.resolve("codebase.yml")
        if (target.exists()) {
            logger.warn("⚠️  codebase.yml already exists — scaffold skipped")
            return@doLast
        }
        val scaffold = """
# codebase.yml — LLM provider credentials and chatbot configuration
# ⚠️  This file contains secrets. It is listed in .gitignore — NEVER commit it.
#
# Structure:
#   ai.<provider>.accounts[]  → liste de comptes (name + email)
#   account.keys[]            → liste de clés nommées (label + key + expiresAt)
#   active                    → sélection active globale
#
# CLI override: ./gradlew <task> -Pcodebase.provider=gemini -Pcodebase.account=pro -Pcodebase.key=prod

active:
  provider: anthropic
  account: ""
  key: ""

ai:
  anthropic:
    defaultAccount: ""
    defaultKey: ""
    models:
      - claude-opus-4-5
      - claude-sonnet-4-5
      - claude-haiku-4-5
    defaultModel: claude-opus-4-5
    accounts:
      - name: ""
        email: ""
        keys:
          - label: ""
            key: ""
            expiresAt: ""

  gemini:
    defaultAccount: ""
    defaultKey: ""
    models:
      - gemini-2.5-pro
      - gemini-2.5-flash
    defaultModel: gemini-2.5-pro
    accounts:
      - name: ""
        email: ""
        keys:
          - label: ""
            key: ""
            expiresAt: ""

  huggingface:
    defaultAccount: ""
    defaultKey: ""
    models:
      - mistralai/Mistral-7B-Instruct-v0.3
      - meta-llama/Llama-3.1-8B-Instruct
    defaultModel: mistralai/Mistral-7B-Instruct-v0.3
    accounts:
      - name: ""
        email: ""
        keys:
          - label: ""
            key: ""
            expiresAt: ""

  mistral:
    defaultAccount: ""
    defaultKey: ""
    models:
      - mistral-large-latest
      - mistral-small-latest
      - codestral-latest
    defaultModel: mistral-large-latest
    accounts:
      - name: ""
        email: ""
        keys:
          - label: ""
            key: ""
            expiresAt: ""

  ollama:
    baseUrl: "http://localhost:11434"
    models:
      - llama3.2
      - codellama
      - mistral
    defaultModel: llama3.2
    accounts:
      - name: local
        email: ""
        keys: []

  grok:
    defaultAccount: ""
    defaultKey: ""
    models:
      - grok-3
      - grok-3-mini
    defaultModel: grok-3
    accounts:
      - name: ""
        email: ""
        keys:
          - label: ""
            key: ""
            expiresAt: ""

  groq:
    defaultAccount: ""
    defaultKey: ""
    models:
      - llama-3.3-70b-versatile
      - llama-3.1-8b-instant
      - mixtral-8x7b-32768
    defaultModel: llama-3.3-70b-versatile
    accounts:
      - name: ""
        email: ""
        keys:
          - label: ""
            key: ""
            expiresAt: ""

chatbot:
  port: 7070
  defaultProvider: anthropic
""".trimIndent()
        target.writeText(scaffold)
        logger.lifecycle("✅ codebase.yml generated")
        val gitignore = projectDir.resolve(".gitignore")
        val entry = "codebase.yml"
        when {
            !gitignore.exists() -> { gitignore.writeText("# codebase — LLM secrets\n$entry\n"); logger.lifecycle("✅ .gitignore created") }
            gitignore.readLines().none { it.trim() == entry } -> { gitignore.appendText("\n# codebase — LLM secrets\n$entry\n"); logger.lifecycle("✅ '$entry' appended to .gitignore") }
            else -> logger.lifecycle("ℹ️  '$entry' already present in .gitignore")
        }
    }
}

project.tasks.register("snapshot") {
    group = "codebase"
    description = "Generates snapshot.adoc with project tree and all source files"
    val rootDir = project.layout.projectDirectory.asFile
    outputs.file(project.layout.projectDirectory.file("snapshot.adoc"))
    doLast {
        val mgr = SnapshotManager()
        with(mgr) { rootDir.generate(logger) }
    }
}

project.tasks.register<JavaExec>("anonymizeWithExpert") {
    group = "codebase"
    description = "Anonymise un fichier via l'expert LangChain4j (EPIC-2 MVP0)"
    classpath = runtime
    mainClass = "codebase.rag.AnonymizationExpertMain"
    val inFile = project.providers.gradleProperty("inputFile").orElse("src/test/resources/datasets/config.yml")
    val outFile = project.providers.gradleProperty("outputFile")
        .map { it.ifBlank { "build/anonymized-output.yml" } }
        .orElse("build/anonymized-output.yml")
    args(inFile.get(), outFile.get())
}
