package lifeos.md.tapilot.runtime

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

data class InstalledNodeRuntime(
    val nodeExecutable: File,
    val runtimeDirectory: File,
    val entrypoint: File,
    val homeDirectory: File,
    val temporaryDirectory: File,
    val manifest: NodeRuntimeManifest,
) {
    // Termux's OpenSSL CA paths belong to its own app sandbox. Use Node's
    // bundled Mozilla roots in Tapilot, with normal certificate verification.
    internal fun command(vararg arguments: String): List<String> =
        listOf(nodeExecutable.absolutePath, "--use-bundled-ca") + arguments
}

class NodeRuntimeProvisioner(private val context: Context) {
    private val installDirectory = File(context.filesDir, NODE_RUNTIME_ASSET_DIRECTORY)

    @Synchronized
    fun install(): InstalledNodeRuntime {
        val assetManifest = readAssetText(NODE_RUNTIME_MANIFEST_ASSET)
        val manifest = NodeRuntimeManifest.parse(assetManifest)
        val installedManifest = File(installDirectory, "manifest.json")
        val installedVersion = runCatching {
            NodeRuntimeManifest.parse(installedManifest.readText()).runtimeVersion
        }.getOrNull()

        if (installedVersion != manifest.runtimeVersion ||
            !File(installDirectory, manifest.entrypoint).isFile
        ) {
            installAssetsAtomically()
        }

        val nodeExecutable = File(context.applicationInfo.nativeLibraryDir, NODE_LIBRARY_NAME)
        if (!nodeExecutable.isFile) {
            throw IOException(
                "Embedded Node runtime is missing at ${nodeExecutable.absolutePath}. " +
                    "Build the APK with the arm64 Node runtime assets.",
            )
        }
        val entrypoint = File(installDirectory, manifest.entrypoint)
        if (!entrypoint.isFile) {
            throw IOException("Node runtime entrypoint is missing: ${entrypoint.absolutePath}")
        }
        return InstalledNodeRuntime(
            nodeExecutable = nodeExecutable,
            runtimeDirectory = installDirectory,
            entrypoint = entrypoint,
            homeDirectory = context.filesDir,
            temporaryDirectory = context.cacheDir,
            manifest = manifest,
        )
    }

    private fun installAssetsAtomically() {
        val staging = File(context.filesDir, "$NODE_RUNTIME_ASSET_DIRECTORY-next")
        deleteTree(staging)
        if (!staging.mkdirs()) throw IOException("Could not create ${staging.absolutePath}")
        try {
            extractBundle(staging)
            val stagedManifest = File(staging, "manifest.json")
            if (!stagedManifest.isFile) throw IOException("Node runtime manifest was not installed")
            deleteTree(installDirectory)
            if (!staging.renameTo(installDirectory)) {
                throw IOException("Could not activate the Node runtime")
            }
        } catch (error: Throwable) {
            deleteTree(staging)
            throw error
        }
    }

    private fun extractBundle(destination: File) {
        val canonicalRoot = destination.canonicalPath + File.separator
        context.assets.open(NODE_RUNTIME_BUNDLE_ASSET).use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val output = File(destination, entry.name)
                    if (!output.canonicalPath.startsWith(canonicalRoot)) {
                        throw IOException("Unsafe Node runtime bundle entry: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        if (!output.exists() && !output.mkdirs()) {
                            throw IOException("Could not create ${output.absolutePath}")
                        }
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { stream -> zip.copyTo(stream) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun readAssetText(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun deleteTree(root: File) {
        if (!root.exists()) return
        root.walkBottomUp().forEach { file ->
            if (!file.delete() && file.exists()) {
                throw IOException("Could not delete ${file.absolutePath}")
            }
        }
    }
}
