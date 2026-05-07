package codebase.extensions

fun String.isValidEmail(): Boolean {
    val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    return matches(emailRegex)
}

fun String.truncate(maxLength: Int, suffix: String = "..."): String =
    if (length > maxLength) take(maxLength - suffix.length) + suffix else this

fun String.toSlug(): String = lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')

fun String.maskEmail(): String {
    val atIndex = indexOf('@')
    if (atIndex <= 1) return this
    val maskedLocal = first() + "*".repeat(atIndex - 2) + this[atIndex - 1]
    return maskedLocal + substring(atIndex)
}

fun String.capitalizeWords(): String = split(" ")
    .joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
