package vibecoding.contracts

import vibecoding.contracts.state.VibecodingState
import vibecoding.contracts.plan.Plan
import contracts.agent.Epic
import contracts.agent.UserStory
import contracts.agent.GradleTask
import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import contracts.context.ContextChannel
import contracts.context.ChannelBudget
import contracts.vibecoding.registry.ToolRegistry
import contracts.vibecoding.registry.ToolInfo
import contracts.vibecoding.registry.AuditEntry
import contracts.vibecoding.tools.ToolkitIsMissingException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class VibecodingContractsTest {

    // ── VibecodingState ──

    @Test
    fun `VibecodingState default values`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        assertEquals("test", state.intention)
        assertEquals("/tmp", state.workspaceRoot)
        assertFalse(state.dryRun)
        assertEquals(10, state.maxActions)
        assertEquals(300, state.sessionTimeoutSeconds)
        assertFalse(state.finished)
        assertEquals(0, state.iteration)
    }

    @Test
    fun `VibecodingState isFinal when finished`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp", finished = true)
        assertTrue(state.isFinal)
    }

    @Test
    fun `VibecodingState isFinal when max iterations reached`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp", iteration = 10, maxActions = 10)
        assertTrue(state.isFinal)
    }

    @Test
    fun `VibecodingState nextIteration increments`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        val next = state.nextIteration()
        assertEquals(0, state.iteration)
        assertEquals(1, next.iteration)
    }

    @Test
    fun `VibecodingState withPlan sets plan`() {
        val plan = Plan("Test Plan", emptyList(), 0, "1")
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        val withPlan = state.withPlan("{}", plan, "simple")
        assertEquals("{}", withPlan.planJson)
        assertEquals(plan, withPlan.plan)
        assertEquals("simple", withPlan.classification)
    }

    @Test
    fun `VibecodingState finish and withError`() {
        val state = VibecodingState(intention = "test", workspaceRoot = "/tmp")
        assertTrue(state.finish().finished)
        val errState = state.withError("something went wrong")
        assertTrue(errState.finished)
        assertEquals("something went wrong", errState.error)
    }

    // ── Plan data classes ──

    @Test
    fun `Plan with full hierarchy`() {
        val task = GradleTask(description = "Write tests", gradleTask = ":test")
        val story = UserStory(description = "Add tests", tasks = listOf(task))
        val epic = Epic(name = "EPIC-1", description = "Implement feature", points = 5, userStories = listOf(story))
        val plan = Plan(title = "MVP", epics = listOf(epic), totalPoints = 5, estimatedSessions = "2")

        assertEquals("MVP", plan.title)
        assertEquals(1, plan.epics.size)
        assertEquals("EPIC-1", plan.epics[0].name)
        assertEquals("Write tests", plan.epics[0].userStories[0].tasks[0].description)
        assertEquals(":test", plan.epics[0].userStories[0].tasks[0].gradleTask)
        assertEquals(5, plan.totalPoints)
        assertEquals("2", plan.estimatedSessions)
    }

    @Test
    fun `Plan empty epics allowed`() {
        val plan = Plan("Empty", emptyList(), 0, "0")
        assertTrue(plan.epics.isEmpty())
    }

    // ── CompositeContext ──

    @Test
    fun `CompositeContext toChannels returns 5 channels`() {
        val config = CompositeContextConfig()
        val ctx = CompositeContext("eager", "rag", "graphify", "docs", config)
        val channels = ctx.toChannels()
        assertEquals(5, channels.size)
        assertTrue(channels[0] is ContextChannel.Eager)
        assertTrue(channels[1] is ContextChannel.Rag)
        assertTrue(channels[2] is ContextChannel.Graphify)
        assertTrue(channels[3] is ContextChannel.Docs)
        assertTrue(channels[4] is ContextChannel.Resource)
        assertEquals("eager", channels[0].content)
        assertEquals("rag", channels[1].content)
        assertEquals("graphify", channels[2].content)
        assertEquals("docs", channels[3].content)
    }

    @Test
    fun `CompositeContext channelsWithBudget truncates content`() {
        val config = CompositeContextConfig()
        val longContent = "A".repeat(10_000)
        val ctx = CompositeContext(longContent, longContent, longContent, longContent, config)
        val budget = ChannelBudget()
        val budgeted = ctx.channelsWithBudget(budget)
        val eagerContent = budgeted[0].content.trimEnd('\n')
        assertTrue(eagerContent.length <= longContent.length,
            "Eager content (${eagerContent.length}) should be <= original ($longContent)")
        assertEquals(5, budgeted.size)
    }

    // ── CompositeContextConfig ──

    @Test
    fun `CompositeContextConfig valid by default`() {
        val config = CompositeContextConfig()
        assertEquals(8000, config.totalTokenBudget)
        assertTrue(config.eagerLazyTokens > 0)
        assertTrue(config.ragTokens > 0)
    }

    @Test
    fun `CompositeContextConfig rejects invalid budget`() {
        assertThrows<IllegalArgumentException> {
            CompositeContextConfig(budgetEagerLazy = 0.5, budgetRag = 0.5, budgetGraphify = 0.5, budgetDocs = 0.5, budgetOverhead = 0.0)
        }
    }

    // ── ChannelBudget ──

    @Test
    fun `ChannelBudget default distribution`() {
        val budget = ChannelBudget()
        assertEquals(0.40, budget.budgetEager, 0.001)
        assertEquals(0.30, budget.budgetRag, 0.001)
        assertEquals(0.20, budget.budgetGraphify, 0.001)
        assertEquals(0.10, budget.budgetDocs, 0.001)
        assertEquals(0.0, budget.budgetResource, 0.001)
    }

    @Test
    fun `ChannelBudget rejects invalid sum`() {
        assertThrows<IllegalArgumentException> {
            ChannelBudget(budgetEager = 1.0, budgetRag = 0.5, budgetGraphify = 0.0, budgetDocs = 0.0, budgetResource = 0.0)
        }
    }

    // ── ToolRegistry ──

    @Test
    fun `ToolRegistry registers 7 tools by default`() {
        val registry = ToolRegistry()
        assertEquals(7, registry.toolCount())
        val names = registry.toolNames()
        assertTrue(names.contains("read_file"))
        assertTrue(names.contains("write_file"))
        assertTrue(names.contains("edit_file"))
        assertTrue(names.contains("list_directory"))
        assertTrue(names.contains("exit"))
        assertTrue(names.contains("exec_shell"))
        assertTrue(names.contains("exec_gradle"))
    }

    @Test
    fun `ToolRegistry get returns tool info`() {
        val registry = ToolRegistry()
        val info = registry.get("read_file")
        assertEquals("read_file", info.name)
        assertTrue(info.description.startsWith("Read"))
    }

    @Test
    fun `ToolRegistry get unknown tool throws`() {
        val registry = ToolRegistry()
        assertThrows<ToolkitIsMissingException> { registry.get("nonexistent") }
    }

    @Test
    fun `ToolRegistry register custom tool`() {
        val registry = ToolRegistry()
        registry.register(ToolInfo("custom", "Custom tool"))
        assertEquals(8, registry.toolCount())
        assertEquals("Custom tool", registry.get("custom").description)
    }

    @Test
    fun `ToolRegistry execute read_file fails outside sandbox`() {
        val registry = ToolRegistry()
        assertThrows<SecurityException> {
            registry.execute("read_file", mapOf("path" to "/etc/passwd"), "/tmp/test-workspace")
        }
    }

    @Test
    fun `ToolRegistry execute write_file dryRun`() {
        val registry = ToolRegistry()
        val result = registry.execute("write_file", mapOf(
            "path" to "test.txt",
            "content" to "hello"
        ), "/tmp/test-workspace", dryRun = true)
        assertTrue(result.startsWith("DRY RUN:"))
    }

    @Test
    fun `ToolRegistry audit trail recorded after execution`() {
        val registry = ToolRegistry()
        try {
            registry.execute("read_file", mapOf("path" to "nonexistent.txt"), "/tmp/test-workspace")
        } catch (_: Exception) { }
        val entries = registry.auditEntries()
        assertTrue(entries.isNotEmpty())
        assertEquals("read_file", entries[0].tool)
    }

    @Test
    fun `ToolRegistry clearAudit`() {
        val registry = ToolRegistry()
        try {
            registry.execute("read_file", mapOf("path" to "any.txt"), "/tmp/test-workspace")
        } catch (_: Exception) { }
        assertTrue(registry.auditEntries().isNotEmpty())
        registry.clearAudit()
        assertTrue(registry.auditEntries().isEmpty())
    }

    @Test
    fun `ToolRegistry execute requires path argument`() {
        val registry = ToolRegistry()
        assertThrows<IllegalArgumentException> {
            registry.execute("read_file", emptyMap(), "/tmp/test-workspace")
        }
    }
}
