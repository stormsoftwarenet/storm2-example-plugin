buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    `java-library`
}

apply<BootstrapPlugin>()

subprojects {
    apply<JavaPlugin>()
    apply(plugin = "java-library")

    group = "net.storm2.plugins"

    project.extra["PluginProvider"] = "Storm 2"
    project.extra["ProjectSupportUrl"] = "https://discord.gg/storm"
    project.extra["PluginLicense"] = "3-Clause BSD License"

    repositories {
        mavenCentral()
        maven("https://repo.runelite.net")
        maven {
            url = uri("https://maven.pkg.github.com/stormsoftwarenet/storm2-public-sdk")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = ""
                password = System.getenv("GITHUB_PACKAGES_PAT")
            }
        }
    }

    dependencies {
        compileOnly(rootProject.libs.runelite.api)
        compileOnly(rootProject.libs.runelite.client)
        compileOnly(rootProject.libs.storm.api)
        compileOnly(rootProject.libs.storm.sdk)

        compileOnly(rootProject.libs.guice)
        compileOnly(rootProject.libs.javax.annotation)
        compileOnly(rootProject.libs.lombok)
        compileOnly(rootProject.libs.pf4j)

        annotationProcessor(rootProject.libs.lombok)
        annotationProcessor(rootProject.libs.pf4j)
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
        }

        withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            dirMode = 493
            fileMode = 420
        }
    }
}
