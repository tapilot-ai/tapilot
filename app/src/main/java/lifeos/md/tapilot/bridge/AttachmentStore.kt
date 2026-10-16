package lifeos.md.tapilot.bridge

import lifeos.md.tapilot.AppText.text as appText
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Private copies allow previews and retries after a document picker grant expires. */
class AttachmentStore(private val directory: File) {
    @Synchronized fun save(value: JSONObject): JSONObject {
        require(value.optString("kind") in setOf("image", "pdf", "text"))
        val id = UUID.randomUUID().toString()
        val data = JSONObject(value.toString()).put("id", id)
        val bytes = data.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 16 * 1024 * 1024) { appText("附件过大，无法保存", "Attachment is too large to save") }
        check(directory.exists() || directory.mkdirs())
        val temporary = File(directory, "$id.tmp")
        try {
            temporary.writeBytes(bytes)
            check(temporary.renameTo(file(id))) { appText("无法保存附件", "Unable to save attachment") }
        } finally { temporary.delete() }
        return data
    }

    @Synchronized fun read(id: String): JSONObject {
        val source = file(id)
        check(source.isFile) { appText("附件已不可用，请重新选择后发送", "Attachment unavailable. Select it again before sending") }
        return JSONObject(source.readText())
    }

    @Synchronized fun retain(ids: Set<String>) {
        directory.listFiles()?.filter { it.extension == "json" && it.nameWithoutExtension !in ids }
            ?.forEach { it.delete() }
    }

    private fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))) { "Invalid attachment id" }
        return File(directory, "$id.json")
    }
}
