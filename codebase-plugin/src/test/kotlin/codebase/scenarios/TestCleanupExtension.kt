package codebase.scenarios

import io.cucumber.java.After
import io.cucumber.java.Before
import org.slf4j.LoggerFactory
import java.io.File

class TestCleanupExtension {

    companion object {
        private val log = LoggerFactory.getLogger(TestCleanupExtension::class.java)
        private var trackedContainerId: String? = null
    }

    @Before
    fun beforeScenario() {
        cleanupOldTempDirectories()
    }

    @After
    fun afterScenario() {
        trackedContainerId?.let { containerId ->
            try {
                log.info("Stopping tracked container $containerId")
                Runtime.getRuntime().exec("docker stop $containerId")
            } catch (e: Exception) {
                log.warn("Failed to stop container $containerId: ${e.message}")
            }
        }
        trackedContainerId = null

        Thread.sleep(200)

        cleanupOldTempDirectories()
    }

    fun trackContainer(containerId: String) {
        trackedContainerId = containerId
    }

    private fun cleanupOldTempDirectories() {
        try {
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)

            File("/tmp").listFiles { f ->
                f.isDirectory && f.name.startsWith("gradle-test-") && f.lastModified() < oneHourAgo
            }?.forEach { dir ->
                try {
                    dir.deleteRecursively()
                    log.debug("Cleaned old temp dir: ${dir.name}")
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
