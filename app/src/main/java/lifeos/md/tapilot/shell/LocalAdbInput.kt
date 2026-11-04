package lifeos.md.tapilot.shell

internal object LocalAdbInput {
    fun port(value: String): Int = value.trim().toIntOrNull()?.takeIf { it in 1..65535 }
        ?: throw IllegalArgumentException("Enter a port between 1 and 65535")
    fun code(value: String): String = value.trim().also { require(it.matches(Regex("[0-9]{6}"))) { "Enter the six-digit pairing code" } }
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
