// build.gradle.kts
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.javelit.core.Server
import java.util.concurrent.CountDownLatch


buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
        classpath("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
        classpath("jakarta.validation:jakarta.validation-api:3.1.0")
        classpath("org.hibernate.validator:hibernate-validator:8.0.1.Final")
        classpath("org.glassfish.expressly:expressly:5.0.0")
        classpath("io.javelit:javelit:0.86.0")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.readme)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}


// ── Tasks ─────────────────────────────────────────────────────────────────────

/**
 * Verifies via logs that ReadmePlantUmlConfig anonymization masks token, userName
 * and userEmail, and preserves all other fields.
 *
 * Usage: ./gradlew verifyReadMeToAnonymizedYaml
 */
tasks.register("verifyReadMeToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that ReadmePlantUmlConfig anonymization masks sensitive fields"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = ReadmeYmlAnonymizer()

        // ── case 1: real token ───────────────────────────────────────────────
        val realToken = "ghp_supersecret123"
        val configReal = ReadmePlantUmlConfig(
            source = SourceConfig(dir = "src", defaultLang = "fr"),
            git = GitConfig(
                token = realToken,
                userName = "my-bot",
                userEmail = "my-bot@company.com",
                watchedBranches = listOf("main", "develop")
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: real token ──────────────────────────────")
        logger.lifecycle(yamlReal)

        check(anonymizer.TOKEN_MASK in yamlReal) { "FAIL case 1: token mask not found" }
        check(realToken !in yamlReal) { "FAIL case 1: real token is visible!" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: userName not anonymized" }
        check("my-bot" !in yamlReal) { "FAIL case 1: real userName is visible!" }
        check(anonymizer.anonymizedEmail() in yamlReal) { "FAIL case 1: anonymized email not found" }
        check("my-bot@company.com" !in yamlReal) { "FAIL case 1: real email is visible!" }
        check("fr" in yamlReal) { "FAIL case 1: defaultLang lost" }
        check("src" in yamlReal) { "FAIL case 1: source.dir lost" }
        check("develop" in yamlReal) { "FAIL case 1: watchedBranches lost" }

        logger.lifecycle("✅ case 1 OK — token, userName, userEmail anonymized — other fields preserved")

        // ── case 2: placeholder token ────────────────────────────────────────
        val configPlaceholder = ReadmePlantUmlConfig(
            git = GitConfig(token = "<YOUR_GITHUB_PAT>")
        )
        val yamlPlaceholder = with(anonymizer) { configPlaceholder.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: placeholder token ───────────────────────")
        logger.lifecycle(yamlPlaceholder)

        check(anonymizer.TOKEN_MASK in yamlPlaceholder) { "FAIL case 2: token mask not found" }
        check("<YOUR_GITHUB_PAT>" !in yamlPlaceholder) { "FAIL case 2: placeholder is visible!" }
        check(anonymizer.anonymizedEmail() in yamlPlaceholder) { "FAIL case 2: anonymized email not found" }

        logger.lifecycle("✅ case 2 OK — placeholder anonymized")

        // ── case 3: empty token ──────────────────────────────────────────────
        val configEmpty = ReadmePlantUmlConfig(git = GitConfig(token = ""))
        val yamlEmpty = with(anonymizer) { configEmpty.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: empty token ─────────────────────────────")
        logger.lifecycle(yamlEmpty)

        check(anonymizer.TOKEN_MASK in yamlEmpty) { "FAIL case 3: token mask not found for empty token" }
        check(anonymizer.anonymizedEmail() in yamlEmpty) { "FAIL case 3: anonymized email not found" }

        logger.lifecycle("✅ case 3 OK — empty token anonymized")

        // ── case 4: idempotency — original object must not be mutated ────────
        val configIdempotent = ReadmePlantUmlConfig(
            git = GitConfig(token = "ghp_idempotence", userName = "real-user")
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("ghp_idempotence" !in yaml2) { "FAIL case 4: token visible on 2nd call — mutation detected!" }
        check("real-user" !in yaml2) { "FAIL case 4: userName visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2) { "FAIL case 4: token mask not found on 2nd call" }
        check(anonymizer.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4: anonymized userName not found on 2nd call" }

        logger.lifecycle("✅ case 4 OK — original not mutated, idempotent")

        // ── case 5: resolvedToken — valid token ──────────────────────────────
        val manager = SnapshotManager()
        with(manager) {
            val gitValid = GitConfig(token = "ghp_valid")
            check(gitValid.resolvedToken() == "ghp_valid") { "FAIL case 5: resolvedToken should return the token" }
            logger.lifecycle("✅ case 5 OK — resolvedToken returns valid token")

            // ── case 6: resolvedToken — blank token ──────────────────────────
            val gitBlank = GitConfig(token = "")
            try {
                gitBlank.resolvedToken()
                error("FAIL case 6: resolvedToken should have thrown")
            } catch (_: IllegalStateException) {
                logger.lifecycle("✅ case 6 OK — resolvedToken throws on blank token")
            }

            // ── case 7: resolvedToken — placeholder token ─────────────────────
            val gitPlaceholder = GitConfig(token = "<YOUR_GITHUB_PAT>")
            try {
                gitPlaceholder.resolvedToken()
                error("FAIL case 7: resolvedToken should have thrown")
            } catch (_: IllegalStateException) {
                logger.lifecycle("✅ case 7 OK — resolvedToken throws on placeholder token")
            }
        }

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifyReadMeToAnonymizedYaml — all cases passed")
    }
}

/**
 * Verifies via logs that SliderConfiguration anonymization masks all sensitive fields
 * and preserves all non-sensitive ones.
 *
 * Usage: ./gradlew verifySliderToAnonymizedYaml
 */
tasks.register("verifySliderToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that SliderConfiguration anonymization masks sensitive fields"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = SliderYmlAnonymizer()

        // ── case 1: real credentials ─────────────────────────────────────────
        val realPassword = "ghp_EkAxg8TgUFBT2ihx8QH6vn0I6k4T"
        val realUsername = "cheroliv"
        val realRepo = "https://github.com/foo/bar.git"
        val realBranch = "production"

        val configReal = SliderConfiguration(
            srcPath = "docs/asciidocRevealJs",
            pushSlider = GitPushConfiguration(
                from = "/docs/asciidocRevealJs",
                to = "cvs",
                branch = realBranch,
                message = "slides show",
                repo = RepositoryConfiguration(
                    name = "slider-gradle",
                    repository = realRepo,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword)
                )
            ),
            ai = AiConfiguration(
                gemini = listOf("FAKE-gemini-key-for-test-only"),
                huggingface = listOf("FAKE-hf-key-for-test-only"),
                mistral = listOf("FAKE-mistral-key-1", "FAKE-mistral-key-2")
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: real credentials ────────────────────────")
        logger.lifecycle(yamlReal)

        check(realPassword !in yamlReal) { "FAIL case 1: real password is visible!" }
        check(realUsername !in yamlReal) { "FAIL case 1: real username is visible!" }
        check(realRepo !in yamlReal) { "FAIL case 1: real repo is visible!" }
        check(realBranch !in yamlReal) { "FAIL case 1: real branch is visible!" }
        check("FAKE-gemini-key-for-test-only" !in yamlReal) { "FAIL case 1: real gemini key is visible!" }
        check("FAKE-hf-key-for-test-only" !in yamlReal) { "FAIL case 1: real huggingface key is visible!" }
        check("FAKE-mistral-key-1" !in yamlReal) { "FAIL case 1: real mistral key is visible!" }
        check("FAKE-mistral-key-2" !in yamlReal) { "FAIL case 1: real mistral key 2 is visible!" }
        check(anonymizer.TOKEN_MASK in yamlReal) { "FAIL case 1: token mask not found" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: anonymous username not found" }
        check(anonymizer.REPO_MASK in yamlReal) { "FAIL case 1: repo mask not found" }
        check(anonymizer.BRANCH_MASK in yamlReal) { "FAIL case 1: branch mask not found" }
        check("docs/asciidocRevealJs" in yamlReal) { "FAIL case 1: srcPath lost" }
        check("slides show" in yamlReal) { "FAIL case 1: message lost" }
        check("slider-gradle" in yamlReal) { "FAIL case 1: repo name lost" }

        logger.lifecycle("✅ case 1 OK — all sensitive fields masked, non-sensitive fields preserved")

        // ── case 2: empty AI lists ────────────────────────────────────────────
        val configEmptyAi = SliderConfiguration(
            pushSlider = GitPushConfiguration(
                from = "", to = "", branch = "feature", message = "",
                repo = RepositoryConfiguration(
                    name = "", repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "cheroliv-bot", password = "ghp_emptyAiSecret99")
                )
            ),
            ai = AiConfiguration()
        )
        val yamlEmptyAi = with(anonymizer) { configEmptyAi.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: empty AI lists ──────────────────────────")
        logger.lifecycle(yamlEmptyAi)

        check("https://github.com/real/repo.git" !in yamlEmptyAi) { "FAIL case 2: real repo is visible!" }
        check("cheroliv-bot" !in yamlEmptyAi) { "FAIL case 2: real username is visible!" }
        check("ghp_emptyAiSecret99" !in yamlEmptyAi) { "FAIL case 2: real password is visible!" }
        check(anonymizer.REPO_MASK in yamlEmptyAi) { "FAIL case 2: repo mask not found" }

        logger.lifecycle("✅ case 2 OK — empty AI lists handled correctly")

        // ── case 3: null pushSlider and null ai ───────────────────────────────
        val configNulls = SliderConfiguration(srcPath = "src/slides")
        val yamlNulls = with(anonymizer) { configNulls.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: null pushSlider and null ai ─────────────")
        logger.lifecycle(yamlNulls)

        check("src/slides" in yamlNulls) { "FAIL case 3: srcPath lost" }

        logger.lifecycle("✅ case 3 OK — null fields handled safely")

        // ── case 4: idempotency — original must not be mutated ────────────────
        val configIdempotent = SliderConfiguration(
            pushSlider = GitPushConfiguration(
                from = "", to = "", branch = "prod", message = "",
                repo = RepositoryConfiguration(
                    name = "", repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "realuser", password = "realpass")
                )
            ),
            ai = AiConfiguration(gemini = listOf("real-gemini-key"))
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("realuser" !in yaml2) { "FAIL case 4: username visible on 2nd call — mutation detected!" }
        check("realpass" !in yaml2) { "FAIL case 4: password visible on 2nd call — mutation detected!" }
        check("real-gemini-key" !in yaml2) { "FAIL case 4: gemini key visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2) { "FAIL case 4: token mask not found on 2nd call" }
        check(anonymizer.ANONYMOUS_USERNAME in yaml2) { "FAIL case 4: anonymous username not found on 2nd call" }

        logger.lifecycle("✅ case 4 OK — original not mutated, idempotent")

        // ── case 5: multiple AI keys per provider ─────────────────────────────
        val configMultiKeys = SliderConfiguration(
            ai = AiConfiguration(
                gemini = listOf("key1", "key2", "key3"),
                huggingface = listOf("hf1", "hf2"),
                mistral = listOf("ms1")
            )
        )
        val yamlMultiKeys = with(anonymizer) { configMultiKeys.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 5: multiple AI keys per provider ───────────")
        logger.lifecycle(yamlMultiKeys)

        listOf("key1", "key2", "key3", "hf1", "hf2", "ms1").forEach { key ->
            check(key !in yamlMultiKeys) { "FAIL case 5: real key '$key' is visible!" }
        }
        val maskCount = yamlMultiKeys.split(anonymizer.TOKEN_MASK).size - 1
        check(maskCount == 6) { "FAIL case 5: expected 6 masked keys, got $maskCount" }

        logger.lifecycle("✅ case 5 OK — all $maskCount AI keys masked across providers")

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifySliderToAnonymizedYaml — all cases passed")
    }
}

/**
 * Verifies via logs that SiteConfiguration anonymization masks all sensitive fields
 * and preserves all non-sensitive ones.
 *
 * Usage: ./gradlew verifySiteToAnonymizedYaml
 */
tasks.register("verifySiteToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that SiteConfiguration anonymization masks sensitive fields"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = SiteYmlAnonymizer()

        // ── case 1: real credentials sur pushPage et pushMaquette ────────────
        val realPassword1 = "ghoS9WycCyJ8pX24Ge9l"
        val realPassword2 = "ghoFRVZTNo8OycCyJ8pX24Ge9l"
        val realUsername = "foo"
        val realRepo1 = "https://github.com/foo/bar.git"
        val realRepo2 = "https://github.com/baz/qux.git"
        val realBranch = "production"

        val configReal = SiteConfiguration(
            bake = BakeConfiguration(srcPath = "site", destDirPath = "bake"),
            pushPage = GitPushConfiguration(
                from = "bake",
                to = "cvs",
                branch = realBranch,
                message = "com.cheroliv.bakery",
                repo = RepositoryConfiguration(
                    name = "pages-content/bakery",
                    repository = realRepo1,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword1)
                )
            ),
            pushMaquette = GitPushConfiguration(
                from = "maquette",
                to = "cvs",
                branch = realBranch,
                message = "cheroliv-maquette",
                repo = RepositoryConfiguration(
                    name = "bakery-maquette",
                    repository = realRepo2,
                    credentials = RepositoryCredentials(username = realUsername, password = realPassword2)
                )
            )
        )
        val yamlReal = with(anonymizer) { configReal.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: pushPage + pushMaquette ─────────────────")
        logger.lifecycle(yamlReal)

        check(realPassword1 !in yamlReal) { "FAIL case 1: real password1 is visible!" }
        check(realPassword2 !in yamlReal) { "FAIL case 1: real password2 is visible!" }
        check(realUsername !in yamlReal) { "FAIL case 1: real username is visible!" }
        check(realRepo1 !in yamlReal) { "FAIL case 1: real repo1 is visible!" }
        check(realRepo2 !in yamlReal) { "FAIL case 1: real repo2 is visible!" }
        check(anonymizer.TOKEN_MASK in yamlReal) { "FAIL case 1: token mask not found" }
        check(anonymizer.ANONYMOUS_USERNAME in yamlReal) { "FAIL case 1: anonymous username not found" }
        check(anonymizer.REPO_MASK in yamlReal) { "FAIL case 1: repo mask not found" }
        check(anonymizer.BRANCH_MASK in yamlReal) { "FAIL case 1: branch mask not found" }
        check("site" in yamlReal) { "FAIL case 1: bake.srcPath lost" }
        check("bake" in yamlReal) { "FAIL case 1: bake.destDirPath lost" }
        check("com.cheroliv.bakery" in yamlReal) { "FAIL case 1: pushPage.message lost" }
        check("cheroliv-maquette" in yamlReal) { "FAIL case 1: pushMaquette.message lost" }
        check("pages-content/bakery" in yamlReal) { "FAIL case 1: pushPage.repo.name lost" }
        check("bakery-maquette" in yamlReal) { "FAIL case 1: pushMaquette.repo.name lost" }

        logger.lifecycle("✅ case 1 OK — pushPage + pushMaquette masked, non-sensitive fields preserved")

        // ── case 2: supabase url et publicKey ────────────────────────────────
        val realUrl = "https://hkgrvjgukx.supabase.co"
        val realPublicKey =
            "eyJhbCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhrZ3lubGZtamh2cXpydmpndWt4Iiwicm9sZSI6ImFub24ifQ"

        val configSupabase = SiteConfiguration(
            pushPage = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            supabase = SupabaseContactFormConfig(
                project = SupabaseProjectInfo(url = realUrl, publicKey = realPublicKey),
                schema = SupabaseDatabaseSchema(
                    contacts = SupabaseTable(
                        name = "public.contacts", rlsEnabled = true,
                        columns = listOf(
                            SupabaseColumn("id", "uuid"),
                            SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("name", "text"),
                            SupabaseColumn("email", "text"),
                            SupabaseColumn("telephone", "text")
                        )
                    ),
                    messages = SupabaseTable(
                        name = "public.messages", rlsEnabled = true,
                        columns = listOf(
                            SupabaseColumn("id", "uuid"),
                            SupabaseColumn("created_at", "timestamptz"),
                            SupabaseColumn("contact_id", "uuid"),
                            SupabaseColumn("subject", "text"),
                            SupabaseColumn("message", "text")
                        )
                    )
                ),
                rpc = SupabaseRpcFunction(
                    name = "public.handle_contact_form",
                    params = listOf(
                        SupabaseParam("p_name", "text"),
                        SupabaseParam("p_email", "text"),
                        SupabaseParam("p_subject", "text"),
                        SupabaseParam("p_message", "text")
                    )
                )
            )
        )
        val yamlSupabase = with(anonymizer) { configSupabase.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: supabase url + publicKey ────────────────")
        logger.lifecycle(yamlSupabase)

        check(realUrl !in yamlSupabase) { "FAIL case 2: real supabase url is visible!" }
        check(realPublicKey !in yamlSupabase) { "FAIL case 2: real publicKey is visible!" }
        check(anonymizer.URL_MASK in yamlSupabase) { "FAIL case 2: url mask not found" }
        check(anonymizer.TOKEN_MASK in yamlSupabase) { "FAIL case 2: token mask not found" }
        check("public.contacts" in yamlSupabase) { "FAIL case 2: schema.contacts.name lost" }
        check("public.messages" in yamlSupabase) { "FAIL case 2: schema.messages.name lost" }
        check("public.handle_contact_form" in yamlSupabase) { "FAIL case 2: rpc.name lost" }

        logger.lifecycle("✅ case 2 OK — supabase url + publicKey masked, schema + rpc preserved")

        // ── case 3: pushSource et pushTemplate nulls ─────────────────────────
        val configNullPush = SiteConfiguration(
            pushPage = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            pushSource = null,
            pushTemplate = null
        )
        val yamlNullPush = with(anonymizer) { configNullPush.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: pushSource + pushTemplate nulls ─────────")
        logger.lifecycle(yamlNullPush)

        logger.lifecycle("✅ case 3 OK — null push fields handled safely (no NPE)")

        // ── case 4: pushSource et pushTemplate non-nulls ──────────────────────
        val realRepo3 = "https://github.com/secret/source.git"
        val configAllPush = SiteConfiguration(
            pushPage = GitPushConfiguration(),
            pushMaquette = GitPushConfiguration(),
            pushSource = GitPushConfiguration(
                branch = "secret-branch",
                repo = RepositoryConfiguration(
                    repository = realRepo3,
                    credentials = RepositoryCredentials(username = "src-user", password = "src-pass")
                )
            ),
            pushTemplate = GitPushConfiguration(
                branch = "template-branch",
                repo = RepositoryConfiguration(
                    repository = "https://github.com/secret/template.git",
                    credentials = RepositoryCredentials(username = "tmpl-user", password = "tmpl-pass")
                )
            )
        )
        val yamlAllPush = with(anonymizer) { configAllPush.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 4: pushSource + pushTemplate non-nulls ──────")
        logger.lifecycle(yamlAllPush)

        check(realRepo3 !in yamlAllPush) { "FAIL case 4: real repo3 is visible!" }
        check("secret-branch" !in yamlAllPush) { "FAIL case 4: secret-branch is visible!" }
        check("src-user" !in yamlAllPush) { "FAIL case 4: src-user is visible!" }
        check("https://github.com/secret/template.git" !in yamlAllPush) { "FAIL case 4: template repo is visible!" }
        check(anonymizer.REPO_MASK in yamlAllPush) { "FAIL case 4: repo mask not found" }

        logger.lifecycle("✅ case 4 OK — pushSource + pushTemplate masked")

        // ── case 5: idempotency ───────────────────────────────────────────────
        val configIdempotent = SiteConfiguration(
            pushPage = GitPushConfiguration(
                branch = "prod",
                repo = RepositoryConfiguration(
                    repository = "https://github.com/real/repo.git",
                    credentials = RepositoryCredentials(username = "idem-user", password = "idem-pass")
                )
            ),
            pushMaquette = GitPushConfiguration()
        )
        with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }
        val yaml2 = with(anonymizer) { configIdempotent.toAnonymizedYaml(mapper) }

        check("idem-user" !in yaml2) { "FAIL case 5: username visible on 2nd call — mutation detected!" }
        check("idem-pass" !in yaml2) { "FAIL case 5: password visible on 2nd call — mutation detected!" }
        check(anonymizer.TOKEN_MASK in yaml2) { "FAIL case 5: token mask not found on 2nd call" }

        logger.lifecycle("✅ case 5 OK — original not mutated, idempotent")

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifySiteToAnonymizedYaml — all cases passed")
    }
}

/**
 * Verifies via logs that CodebaseConfiguration anonymization masks all API keys
 * and preserves all non-sensitive fields.
 *
 * Usage: ./gradlew verifyCodebaseToAnonymizedYaml
 */
tasks.register("verifyCodebaseToAnonymizedYaml") {
    group = "codebase"
    description = "Verifies via logs that CodebaseConfiguration anonymization masks API keys"

    doLast {
        val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        val anonymizer = CodebaseYmlAnonymizer()

        // ── case 1: anthropic — clé réelle ───────────────────────────────────
        val realAnthropic = "sk-ant-api03-supersecret"
        val config1 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    defaultAccount = "perso",
                    defaultKey = "chatbot",
                    models = listOf("claude-opus-4-5", "claude-sonnet-4-5"),
                    defaultModel = "claude-opus-4-5",
                    accounts = listOf(
                        LlmAccount(
                            "perso", "p@gmail.com", listOf(
                                NamedApiKey("chatbot", realAnthropic, "2026-12-31")
                            )
                        )
                    )
                )
            )
        )
        val yaml1 = with(anonymizer) { config1.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 1: anthropic real key ──────────────────────")
        logger.lifecycle(yaml1)

        check(realAnthropic !in yaml1) { "FAIL case 1: real anthropic key is visible!" }
        check(anonymizer.TOKEN_MASK in yaml1) { "FAIL case 1: token mask not found" }
        check("perso" in yaml1) { "FAIL case 1: account name lost" }
        check("p@gmail.com" in yaml1) { "FAIL case 1: account email lost" }
        check("chatbot" in yaml1) { "FAIL case 1: key label lost" }
        check("2026-12-31" in yaml1) { "FAIL case 1: expiresAt lost" }
        check("claude-opus-4-5" in yaml1) { "FAIL case 1: model lost" }

        logger.lifecycle("✅ case 1 OK — anthropic key masked, metadata preserved")

        // ── case 2: tous les providers sauf ollama ────────────────────────────
        val realGemini = "FAKE-gemini-AIzaSy-test"
        val realHf = "FAKE-hf-token-test"
        val realMistral = "FAKE-mistral-key-test"
        val realGrok = "FAKE-grok-key-test"
        val realGroq = "FAKE-groq-gsk-test"

        val config2 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                gemini = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "g@gmail.com", listOf(
                                NamedApiKey("main", realGemini, "2026-12-31")
                            )
                        )
                    )
                ),
                huggingface = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "hf@gmail.com", listOf(
                                NamedApiKey("main", realHf, "")
                            )
                        )
                    )
                ),
                mistral = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "ms@gmail.com", listOf(
                                NamedApiKey("main", realMistral, "2027-03-01"),
                                NamedApiKey("spare", "mst-spare-key", "")
                            )
                        )
                    )
                ),
                grok = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount("perso", "grok@x.com", listOf(NamedApiKey("main", realGrok, "")))
                    )
                ),
                groq = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount("perso", "groq@groq.com", listOf(NamedApiKey("main", realGroq, "")))
                    )
                )
            )
        )
        val yaml2 = with(anonymizer) { config2.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 2: gemini + huggingface + mistral + grok + groq")
        logger.lifecycle(yaml2)

        listOf(realGemini, realHf, realMistral, realGrok, realGroq, "mst-spare-key").forEach { k ->
            check(k !in yaml2) { "FAIL case 2: real key '$k' is visible!" }
        }
        check("g@gmail.com" in yaml2) { "FAIL case 2: gemini email lost" }
        check("2026-12-31" in yaml2) { "FAIL case 2: expiresAt lost" }
        check("spare" in yaml2) { "FAIL case 2: spare label lost" }

        logger.lifecycle("✅ case 2 OK — all providers masked, metadata preserved")

        // ── case 3: ollama — baseUrl non masquée, pas de clés ─────────────────
        val config3 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                ollama = LlmProviderConfig(
                    baseUrl = "http://localhost:11434",
                    models = listOf("llama3.2", "codellama"),
                    accounts = listOf(LlmAccount("local", "", emptyList()))
                )
            )
        )
        val yaml3 = with(anonymizer) { config3.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 3: ollama baseUrl non masquée ──────────────")
        logger.lifecycle(yaml3)

        check("http://localhost:11434" in yaml3) { "FAIL case 3: ollama baseUrl was masked!" }
        check("llama3.2" in yaml3) { "FAIL case 3: ollama model lost" }
        check("codellama" in yaml3) { "FAIL case 3: ollama model codellama lost" }

        logger.lifecycle("✅ case 3 OK — ollama baseUrl preserved (not a secret)")

        // ── case 4: clés vides → pas de masque ───────────────────────────────
        val config4 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount("perso", "p@g.com", listOf(NamedApiKey("main", "", "")))
                    )
                )
            )
        )
        val yaml4 = with(anonymizer) { config4.toAnonymizedYaml(mapper) }
        val maskCount = yaml4.lines().count { anonymizer.TOKEN_MASK in it }

        logger.lifecycle("── case 4: clés vides — pas de masque ──────────────")
        logger.lifecycle(yaml4)

        check(maskCount == 0) { "FAIL case 4: expected 0 masks for blank keys, got $maskCount" }

        logger.lifecycle("✅ case 4 OK — blank keys produce no spurious masks")

        // ── case 5: active + chatbot préservés ───────────────────────────────
        val config5 = CodebaseConfiguration(
            active = ActiveSelection(provider = "mistral", account = "pro", key = "prod"),
            chatbot = ChatbotConfig(port = 9090, defaultProvider = "mistral")
        )
        val yaml5 = with(anonymizer) { config5.toAnonymizedYaml(mapper) }

        logger.lifecycle("── case 5: active + chatbot préservés ──────────────")
        logger.lifecycle(yaml5)

        check("mistral" in yaml5) { "FAIL case 5: active.provider lost" }
        check("pro" in yaml5) { "FAIL case 5: active.account lost" }
        check("prod" in yaml5) { "FAIL case 5: active.key lost" }
        check("9090" in yaml5) { "FAIL case 5: chatbot.port lost" }

        logger.lifecycle("✅ case 5 OK — active + chatbot config preserved")

        // ── case 6: idempotency — original must not be mutated ────────────────
        val config6 = CodebaseConfiguration(
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "p@g.com", listOf(
                                NamedApiKey("chatbot", "sk-ant-idempotent", "2027-01-01")
                            )
                        )
                    )
                ),
                gemini = LlmProviderConfig(
                    accounts = listOf(
                        LlmAccount(
                            "perso", "g@g.com", listOf(
                                NamedApiKey("main", "AIza-idempotent", "")
                            )
                        )
                    )
                )
            )
        )
        with(anonymizer) { config6.toAnonymizedYaml(mapper) }
        val yaml6b = with(anonymizer) { config6.toAnonymizedYaml(mapper) }

        check("sk-ant-idempotent" !in yaml6b) { "FAIL case 6: anthropic key visible on 2nd call — mutation!" }
        check("AIza-idempotent" !in yaml6b) { "FAIL case 6: gemini key visible on 2nd call — mutation!" }
        check(anonymizer.TOKEN_MASK in yaml6b) { "FAIL case 6: token mask not found on 2nd call" }

        logger.lifecycle("✅ case 6 OK — original not mutated, idempotent")

        // ── case 7: resolveActiveKey — sélection via active + CLI override ────
        val localConfig = CodebaseYmlConfig()
        val config7 = CodebaseConfiguration(
            active = ActiveSelection(provider = "anthropic", account = "perso", key = "chatbot"),
            ai = AiProvidersConfig(
                anthropic = LlmProviderConfig(
                    defaultAccount = "perso",
                    defaultKey = "chatbot",
                    accounts = listOf(
                        LlmAccount(
                            "perso", "p@g.com", listOf(
                                NamedApiKey("chatbot", "sk-ant-chatbot", "2027-06-01"),
                                NamedApiKey("ci", "sk-ant-ci", "")
                            )
                        ),
                        LlmAccount(
                            "pro", "pro@c.com", listOf(
                                NamedApiKey("prod", "sk-ant-prod", "2025-01-01") // expirée
                            )
                        )
                    )
                )
            )
        )

        val resolved = localConfig.resolveActiveKey(config7, logger)
        check(resolved?.key == "sk-ant-chatbot") { "FAIL case 7: expected chatbot key, got ${resolved?.key}" }
        check(resolved.label == "chatbot") { "FAIL case 7: expected label 'chatbot', got ${resolved?.label}" }

        val resolvedCli = localConfig.resolveActiveKey(
            config7, logger,
            cliProvider = "anthropic", cliAccount = "pro", cliKey = "prod"
        )
        check(resolvedCli?.key == "sk-ant-prod") {
            "FAIL case 7: CLI override failed, got ${resolvedCli?.key}"
        }

        val resolvedCliKey = localConfig.resolveActiveKey(
            config7, logger,
            cliKey = "ci"
        )
        check(resolvedCliKey?.key == "sk-ant-ci") { "FAIL case 7: CLI key override failed, got ${resolvedCliKey?.key}" }

        logger.lifecycle("✅ case 7 OK — resolveActiveKey: active, CLI override, expired key warning")

        logger.lifecycle("════════════════════════════════════════════════════")
        logger.lifecycle("✅ verifyCodebaseToAnonymizedYaml — all cases passed")
    }
}

/**
 * Scaffolds codebase.yml with empty fields and registers it in .gitignore.
 *
 * - Warning + skip if codebase.yml already exists (no overwrite).
 * - Appends codebase.yml to .gitignore if not already present.
 *
 * Usage: ./gradlew scaffoldCodebaseYml
 */
tasks.register("scaffoldCodebaseYml") {
    group = "codebase"
    description = "Generates codebase.yml scaffold and registers it in .gitignore"

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val target = projectDir.resolve("codebase.yml")

        if (target.exists()) {
            logger.warn("⚠️  codebase.yml already exists — scaffold skipped (delete it manually to regenerate)")
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
        logger.lifecycle("✅ codebase.yml generated at ${target.absolutePath}")

        val gitignore = projectDir.resolve(".gitignore")
        val entry = "codebase.yml"

        when {
            !gitignore.exists() -> {
                gitignore.writeText("# codebase — LLM secrets\n$entry\n")
                logger.lifecycle("✅ .gitignore created with entry '$entry'")
            }

            gitignore.readLines().none { it.trim() == entry } -> {
                gitignore.appendText("\n# codebase — LLM secrets\n$entry\n")
                logger.lifecycle("✅ '$entry' appended to existing .gitignore")
            }

            else ->
                logger.lifecycle("ℹ️  '$entry' already present in .gitignore")
        }
    }
}


/**
 * Démarre un serveur Javelit embarqué exposant le chatbot sur le port configuré
 * dans codebase.yml (défaut : 7070).
 *
 * Le LLM est mocké — seule l'UI et le cycle démarrage/arrêt sont validés ici.
 * L'intégration réelle (LangChain4j + resolveActiveKey) est planifiée en roadmap.
 *
 * Surcharge CLI :
 *   ./gradlew --no-daemon chatbot
 *   ./gradlew --no-daemon chatbot -Pcodebase.port=8080
 *   ./gradlew --no-daemon chatbot -Pcodebase.provider=ollama
 *
 * Arrêt propre :
 *   Ctrl+C → shutdown hook → latch.countDown() → server.stop() → Runtime.halt(0)
 *
 * Note : --no-daemon est obligatoire. Sans lui, Gradle exécute la tâche dans
 *         le Gradle Daemon — Runtime.halt(0) ne tue pas le daemon, le serveur
 *         Javelit reste accessible après Ctrl+C. Gradle 9.x ne permet pas de
 *         désactiver le daemon programmatiquement depuis le build script
 *         (StartParameter.isNoDaemonBuild supprimé en Gradle 9).
 *
 * Usage: ./gradlew --no-daemon chatbot
 */
tasks.register("chatbot") {
    group = "codebase"
    description = "Starts an embedded Javelit chatbot (LLM mocked) on port from codebase.yml"

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val taskLogger = logger

        // ── Config ────────────────────────────────────────────────────────────
        val localConfig = CodebaseYmlConfig()
        val cfg = with(localConfig) { CodebaseConfiguration.load(projectDir) }

        val cliProvider = project.findProperty("codebase.provider") as String?
        val cliPort = (project.findProperty("codebase.port") as String?)?.toIntOrNull()

        val providerName = (cliProvider?.takeIf { it.isNotBlank() }
            ?: cfg.active.provider.takeIf { it.isNotBlank() }
            ?: "anthropic").lowercase()

        val port = cliPort ?: cfg.chatbot.port

        taskLogger.lifecycle("[chatbot] Port     : $port")
        taskLogger.lifecycle("[chatbot] Provider : $providerName (mocké)")
        taskLogger.lifecycle("[chatbot] Modèles  : ${availableModels(cfg, providerName)}")

        // ── Démarrage serveur Javelit (non-bloquant) ──────────────────────────
        val server = Server.builder({ chatbotApp(cfg, providerName) }, port).build()
        server.start()

        taskLogger.lifecycle("✅ Chatbot démarré → http://localhost:$port")
        taskLogger.lifecycle("   Ctrl+C pour arrêter")

        // ── Latch + shutdown hook ──────────────────────────────────────────
        val latch = CountDownLatch(1)

        Runtime.getRuntime().addShutdownHook(Thread {
            taskLogger.lifecycle("[chatbot] Arrêt en cours…")
            latch.countDown()
        })

        latch.await()

        taskLogger.lifecycle("[chatbot] Arrêté proprement.")
        Runtime.getRuntime().halt(0)
    }
}

/**
 * Generates snapshot.adoc — a full AsciiDoc snapshot of the project sources.
 * Replaces any existing snapshot.adoc at the project root.
 * snapshot.adoc is the write target only — its content is never collected.
 * readme.yml, slider-context.yml, site.yml and codebase.yml are rendered
 * with anonymized content.
 *
 * Usage: ./gradlew snapshot
 */
tasks.register("snapshot") {
    group = "codebase"
    description = "Generates snapshot.adoc with project tree and all source files"

    val rootDir = layout.projectDirectory.asFile

    outputs.file(layout.projectDirectory.file("snapshot.adoc"))

    doLast {
        val taskLogger = logger
        val manager = SnapshotManager()
        with(manager) { rootDir.generate(taskLogger) }
    }
}