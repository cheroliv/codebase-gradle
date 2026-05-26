package codebase.koog.tracking

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenTrackerTest {

    @Test
    fun `trackPrompt tracks estimated tokens and increments totalCalls`() {
        val tracker = TokenTracker()
        tracker.trackPrompt("Hello, how are you today?")
        assertEquals(1, tracker.totalCalls)
        assertTrue(tracker.promptTokens > 0, "Prompt tokens should be estimated > 0")
        assertEquals(0L, tracker.completionTokens, "Completion tokens should remain 0")
    }

    @Test
    fun `trackPromptAndCompletion tracks both prompt and completion tokens`() {
        val tracker = TokenTracker()
        val result = tracker.trackPromptAndCompletion("What is 2+2?") { _ -> "4" }
        assertEquals("4", result)
        assertEquals(1, tracker.totalCalls)
        assertTrue(tracker.promptTokens > 0, "Prompt tokens should be estimated > 0")
        assertTrue(tracker.completionTokens > 0, "Completion tokens should be estimated > 0")
    }

    @Test
    fun `reset zeroes all counters`() {
        val tracker = TokenTracker()
        tracker.trackPrompt("Hello, world!")
        tracker.trackPromptAndCompletion("Test") { _ -> "Response" }
        assertTrue(tracker.totalCalls > 0)
        assertTrue(tracker.promptTokens > 0)
        assertTrue(tracker.completionTokens > 0)

        tracker.reset()
        assertEquals(0, tracker.totalCalls)
        assertEquals(0L, tracker.promptTokens)
        assertEquals(0L, tracker.completionTokens)
    }

    @Test
    fun `totalCalls increments correctly over multiple calls`() {
        val tracker = TokenTracker()
        tracker.trackPrompt("First")
        tracker.trackPrompt("Second")
        tracker.trackPromptAndCompletion("Third") { _ -> "OK" }
        assertEquals(3, tracker.totalCalls)
    }

    @Test
    fun `estimatedCost returns correct cost for known model`() {
        val tracker = TokenTracker()
        tracker.trackPrompt("Hello, world. ".repeat(50))
        tracker.trackPromptAndCompletion("Test query. ".repeat(25)) { _ -> "Response. ".repeat(25) }
        val cost = tracker.estimatedCost("deepseek-v4-pro")
        assertTrue(cost > 0.0, "Cost should be positive for known model")
    }

    @Test
    fun `estimatedCost returns 0 for unknown model`() {
        val tracker = TokenTracker()
        tracker.trackPrompt("Hello")
        val cost = tracker.estimatedCost("unknown-model-xyz")
        assertEquals(0.0, cost)
    }

    @Test
    fun `trackPromptAndCompletion wraps block functionally`() {
        val tracker = TokenTracker()
        var called = false
        val result = tracker.trackPromptAndCompletion("input") { input ->
            called = true
            assertEquals("input", input)
            "output-$input"
        }
        assertTrue(called)
        assertEquals("output-input", result)
    }

    @Test
    fun `default estimator produces non-negative token counts`() {
        assertEquals(0, TokenTracker.defaultEstimateTokens(""))
        assertTrue(TokenTracker.defaultEstimateTokens("Hello, world!") > 0)
        assertTrue(TokenTracker.defaultEstimateTokens("a") > 0)
        assertTrue(TokenTracker.defaultEstimateTokens("A somewhat longer sentence for testing.") > 3)
    }
}
