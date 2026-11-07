plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}
group = "dev.agentgui"
version = "0.1.0"
repositories { mavenCentral(); intellijPlatform { defaultRepositories() } }
dependencies {
    intellijPlatform {
        local(providers.gradleProperty("studioPath").orElse("/Applications/Android Studio.app"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin { jvmToolchain(21) }
intellijPlatform {
    pluginConfiguration {
        name = "Agent GUI"
        ideaVersion { sinceBuild = "261"; untilBuild = "261.*" }
    }
    buildSearchableOptions = false
}
tasks.test { useJUnitPlatform() }
val buildWebview by tasks.registering(Exec::class) {
    workingDir("webview")
    commandLine("npm", "run", "build")
    inputs.dir("webview/src")
    inputs.files("webview/package-lock.json", "webview/index.html", "webview/vite.config.ts", "webview/tsconfig.json")
    outputs.dir("webview/dist")
}
tasks.processResources {
    dependsOn(buildWebview)
    from("webview/dist") { into("webview") }
}
