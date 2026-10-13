package lifeos.md.tapilot.bridge

/** Preserve the damaged record until the user replaces or deletes this provider's key. */
internal fun readCredential(preset: String, encrypted: Any?, decrypt: (String) -> String): String? {
    if (encrypted == null) return null
    return try {
        require(encrypted is String)
        decrypt(encrypted)
    } catch (error: Exception) {
        throw IllegalStateException("无法读取 $preset 的密钥，请在设置中重新填写或重置该服务 / Replace or reset this provider's API key", error)
    }
}
