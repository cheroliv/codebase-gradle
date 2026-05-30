package codebase.scenarios

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Runner Cucumber dédié aux scénarios @epic_v_pool (Pool Ollama GPT-OSS-120B).
 *
 * Cible le fichier epic_v_pool.feature et filtre les scénarios tagués @epic_v_pool.
 */
@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codebase.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-epic-v-pool.html, json:build/reports/cucumber-epic-v-pool.json"
)
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/features/epic_v_pool.feature")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@epic_v_pool")
class EpicVPoolCucumberRunner
