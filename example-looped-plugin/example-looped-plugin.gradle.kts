version = "0.0.1"

project.extra["PluginName"] = "Example Looped Plugin"
project.extra["PluginDescription"] =
    "A type of plugin of which the plugin logic is contained within a single 'loop' function"

tasks {
    jar {
        manifest {
            attributes(
                mapOf(
                    "Plugin-Version" to project.version,
                    "Plugin-Id" to nameToId(project.extra["PluginName"] as String),
                    "Plugin-Provider" to project.extra["PluginProvider"],
                    "Plugin-Description" to project.extra["PluginDescription"],
                    "Plugin-License" to project.extra["PluginLicense"]
                )
            )
        }
    }
}
