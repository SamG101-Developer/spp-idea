// The CLion-only part of the plugin. It is compiled against
// CLion rather than IntelliJ IDEA, and is merged into the main
// plugin jar; plugin.xml only loads it (spp-clion.xml) when
// the IDE has the CLion plugin, so other IDEs never see it.
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // The S++ classes this module builds on (SppRunProfile and
    // co.). Only compiled against: at runtime they are in the
    // same plugin.
    compileOnly(project(":"))

    intellijPlatform {
        clion(providers.gradleProperty("platformVersion"))
        bundledPlugins("com.intellij.clion", "com.intellij.nativeDebug")
    }
}
