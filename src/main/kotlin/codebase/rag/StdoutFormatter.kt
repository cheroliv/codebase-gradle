package codebase.rag

object StdoutFormatter {

    fun section(tag: Tag, content: String) {
        println("$tag $content")
    }

    fun ctx(content: String) = section(Tag.CTX, content)
    fun plan(content: String) = section(Tag.PLAN, content)
    fun result(content: String) = section(Tag.RESULT, content)

    fun log(ctx: String, plan: String, result: String) {
        ctx(ctx)
        plan(plan)
        result(result)
    }

    fun banner(title: String) {
        val bar = "═".repeat(title.length + 6)
        println(bar)
        println("  $title")
        println(bar)
        println()
    }

    fun separator() = println("─".repeat(60))

    @JvmInline
    value class Tag(val label: String) {
        override fun toString(): String = "[$label]"

        companion object {
            val CTX = Tag("CTX")
            val PLAN = Tag("PLAN")
            val RESULT = Tag("RESULT")
        }
    }
}
