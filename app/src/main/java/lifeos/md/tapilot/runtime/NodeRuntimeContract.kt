package lifeos.md.tapilot.runtime

import org.json.JSONObject

internal const val NODE_EVENT_PREFIX = "MIDSCENE_EVENT "
internal const val NODE_LIBRARY_NAME = "libnodebin.so"
internal const val NODE_RUNTIME_ASSET_DIRECTORY = "node-runtime"
internal const val NODE_RUNTIME_BUNDLE_ASSET = "node-runtime-bundle.zip"
internal const val NODE_RUNTIME_MANIFEST_ASSET = "node-runtime-manifest.json"

data class NodeRuntimeManifest(
    val schemaVersion: Int,
    val runtimeVersion: String,
    val entrypoint: String,
    val minimumNodeMajor: Int,
) {
    init {
        require(schemaVersion == 1) { "Unsupported Node runtime manifest schema: $schemaVersion" }
        require(runtimeVersion.isNotBlank()) { "Node runtime version must not be blank" }
        require(entrypoint.matches(Regex("[A-Za-z0-9._-]+"))) {
            "Node runtime entrypoint must be a file name"
        }
        require(minimumNodeMajor >= 20) { "Embedded Node must be version 20 or newer" }
    }

    companion object {
        fun parse(source: String): NodeRuntimeManifest {
            val json = JSONObject(source)
            return NodeRuntimeManifest(
                schemaVersion = json.getInt("schemaVersion"),
                runtimeVersion = json.getString("runtimeVersion"),
                entrypoint = json.getString("entrypoint"),
                minimumNodeMajor = json.getInt("minimumNodeMajor"),
            )
        }
    }
}

data class NodeRuntimeEvent(
    val type: String,
    val timestamp: String,
    val payload: String,
) {
    companion object {
        fun parse(line: String): NodeRuntimeEvent? {
            if (!line.startsWith(NODE_EVENT_PREFIX)) return null
            val payload = line.removePrefix(NODE_EVENT_PREFIX)
            val json = JSONObject(payload)
            return NodeRuntimeEvent(
                type = json.getString("type"),
                timestamp = json.getString("timestamp"),
                payload = payload,
            )
        }
    }
}
