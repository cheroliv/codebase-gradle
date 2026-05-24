package codebase.rag

import education.cccp.contracts.context.ChannelBudget
import education.cccp.contracts.context.CompositeContext
import education.cccp.contracts.context.ContextChannel
import org.slf4j.LoggerFactory
import java.io.File

class OpencodeInjector {

    private val log = LoggerFactory.getLogger(OpencodeInjector::class.java)

    fun inject(context: CompositeContext): String {
        val channels = context.toChannels()
        return injectChannelList(channels)
    }

    fun injectChannels(channels: List<ContextChannel>): String {
        return injectChannelList(channels)
    }

    fun injectChannels(channels: List<ContextChannel>, budget: ChannelBudget): String {
        return injectChannelList(budget.applyBudget(channels))
    }

    private fun injectChannelList(channels: List<ContextChannel>): String {
        val sb = StringBuilder()

        for (channel in channels) {
            val label = if (channel.sectionHeader.isNotBlank()) "[${channel.sectionHeader}]" else channel.source
            sb.appendLine("[$label] ${channel.content.trimStart()}")
            sb.appendLine()
        }

        val output = sb.toString()
        log.info("OpencodeInjector: injected {} chars across {} channels (active={})",
            output.length,
            channels.size,
            channels.count { it.content.isNotEmpty() })

        return output
    }

    fun injectToFile(context: CompositeContext, outputFile: File): File {
        val content = inject(context)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(content)
        log.info("Context injected to ${outputFile.absolutePath} ({} chars)", content.length)
        return outputFile
    }
}
