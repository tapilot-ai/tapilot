package lifeos.md.tapilot.bridge

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject

class AttachmentStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun privateCopiesSurviveAndUnusedCopiesAreRemoved() {
        val store = AttachmentStore(folder.newFolder("attachments"))
        val a = store.save(JSONObject().put("kind", "text").put("name", "note.txt").put("text", "original content"))
        val b = store.save(JSONObject().put("kind", "image").put("name", "photo.png"))
        store.retain(setOf(a.getString("id")))
        assertEquals("original content", store.read(a.getString("id")).getString("text"))
        assertThrows(IllegalStateException::class.java) { store.read(b.getString("id")) }
    }
    @Test fun referencesCannotEscapeThePrivateDirectory() {
        val store = AttachmentStore(folder.newFolder("attachments"))
        assertThrows(IllegalArgumentException::class.java) { store.read("../../other") }
    }
}
