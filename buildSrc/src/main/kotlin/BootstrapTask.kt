import com.google.gson.Gson
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.extra
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.nio.file.Paths
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

open class BootstrapTask : DefaultTask() {

    private fun formatDate(date: Date?) = with(date ?: Date()) {
        SimpleDateFormat("yyyy-MM-dd").format(this)
    }

    private fun hash(file: ByteArray): String {
        return MessageDigest.getInstance("SHA-512").digest(file).fold("") { str, it -> str + "%02x".format(it) }
            .toUpperCase()
    }

    private fun getBootstrap(filename: String): MutableList<Bootstrap> {
        return try {
            FileReader(filename).use { Gson().fromJson(it, Array<Bootstrap>::class.java).toMutableList() }
        } catch (e: FileNotFoundException) {
            mutableListOf()
        }
    }

    @TaskAction
    fun boostrap() {
        if (project == project.rootProject) {
            val bootstrapDir = File("${project.projectDir}")
            val bootstrapReleaseDir = File("${project.projectDir}/release")

            bootstrapReleaseDir.mkdirs()

            val bootstrapMap = getBootstrap("$bootstrapDir/plugins.json")
                .associateBy { it.id }
                .toMutableMap()

            project.subprojects.forEach {
                if (it.project.properties.containsKey("PluginName") && it.project.properties.containsKey("PluginDescription")) {
                    val pluginFile = File("${it.project.buildDir}/libs/${it.project.name}-${it.project.version}.jar")

                    val sha512 = hash(pluginFile.readBytes())
                    val release = Bootstrap.Release(
                        version = it.project.version as String,
                        requires = "^1.0.0",
                        date = formatDate(Date()),
                        url = "https://raw.githubusercontent.com/$githubUri/${it.project.name}-${it.project.version}.jar",
                        sha512sum = sha512
                    )

                    val id = nameToId(it.project.extra.get("PluginName") as String)
                    val existingBootstrap = bootstrapMap[id]
                    if (existingBootstrap != null) {
                        val existingRelease = existingBootstrap.releases.firstOrNull { it.version == release.version }
                        if (existingRelease != null) {
                            existingBootstrap.releases[existingBootstrap.releases.indexOf(existingRelease)] = release
                        } else {
                            existingBootstrap.releases.add(release)
                        }
                    } else {
                        bootstrapMap[id] = Bootstrap(
                            name = it.project.extra.get("PluginName") as String,
                            id = id,
                            description = it.project.extra.get("PluginDescription") as String,
                            provider = it.project.extra.get("PluginProvider") as String,
                            projectUrl = it.project.extra.get("ProjectSupportUrl") as String,
                            releases = mutableListOf(release)
                        )
                    }

                    pluginFile.copyTo(
                        Paths.get(bootstrapReleaseDir.toString(), "${it.project.name}-${it.project.version}.jar")
                            .toFile(),
                        overwrite = true
                    )
                }
            }

            val json = JsonOutput.prettyPrint(Gson().toJson(bootstrapMap.values))

            File(bootstrapDir, "plugins.json").printWriter().use { out ->
                out.println(json)
            }
        }
    }

    data class Bootstrap(
        val projectUrl: String,
        val provider: String,
        val name: String,
        val description: String,
        val id: String,
        val releases: MutableList<Release>
    ) {
        data class Release(
            val date: String,
            val sha512sum: String,
            val version: String,
            val url: String,
            val requires: String
        )
    }
}
