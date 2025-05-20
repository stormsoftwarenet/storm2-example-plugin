rootProject.name = "storm2-example-plugin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "example-looped-plugin",
    "JG-example-plugin",
    "JG-tutorial-island",
)

for (project in rootProject.children) {
    project.apply {
        projectDir = file(name)
        buildFileName = "$name.gradle.kts"

        require(projectDir.isDirectory) { "Project '${project.path} must have a $projectDir directory" }
        require(buildFile.isFile) { "Project '${project.path} must have a $buildFile build script" }
    }
}