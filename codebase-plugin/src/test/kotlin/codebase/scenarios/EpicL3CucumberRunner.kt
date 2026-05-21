package codebase.scenarios

import io.cucumber.junit.platform.engine.Constants.*
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

/**
 * Runner Cucumber dédié aux scénarios @epic_l_3 (Pipeline KoogAugmentedContextGraph).
 *
 * Baby-step #2 : isole les 7 scénarios @epic_l_3 du runner principal
 * pour un diagnostic rapide et une boucle TDD/BDD courte.
 *
 * Pattern aligné sur plantuml-gradle : PicoContainer injecte [AugmentedPlanningWorld]
 * dans [AugmentedPlanningSteps] via constructeur.
 */
@Suite
@IncludeEngines("cucumber")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codebase.scenarios")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-epic-l3.html, json:build/reports/cucumber-epic-l3.json"
)
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/features/epic_l_3_augmented_planning.feature")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@epic_l_3")
class EpicL3CucumberRunner
