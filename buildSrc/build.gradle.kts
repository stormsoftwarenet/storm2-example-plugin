plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    implementation(group = "org.json", name = "json", version = "20190722")
    implementation(group = "com.savvasdalkitsis", name = "json-merge", version = "0.0.5")
    implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.2.2")

    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
}
