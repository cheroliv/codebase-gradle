package codebase.scenarios

import org.junit.platform.suite.api.*
import io.cucumber.junit.platform.engine.Constants.*

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "codebase.scenarios.ocr")
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "pretty, html:build/reports/cucumber-ocr.html, json:build/reports/cucumber-ocr.json"
)
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/features")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@ocr")
class OcrCucumberRunner
