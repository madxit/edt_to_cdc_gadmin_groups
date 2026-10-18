import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.attributes.Attribute

val appVersion = "1.0.0"
val appName = "EdtToCdcSync"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop") {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }
    
    val fatJarNatives by configurations.creating {
        attributes {
            attribute(Attribute.of("ui", String::class.java), "awt")
        }
    }
    dependencies {
        fatJarNatives(compose.desktop.macos_arm64)
        fatJarNatives(compose.desktop.macos_x64)
        fatJarNatives(compose.desktop.linux_x64)
        fatJarNatives(compose.desktop.windows_x64)
    }

    val generateBuildConfig = tasks.register("generateBuildConfig") {
        val outputDir = layout.buildDirectory.dir("generated/edttocdc/kotlin")
        outputs.dir(outputDir)
        doLast {
            val configFile = file("${outputDir.get()}/it/madx/edttocdc/BuildConfig.kt")
            configFile.parentFile.mkdirs()
            configFile.writeText("""
                package it.madx.edttocdc

                object BuildConfig {
                    const val APP_VERSION = "$appVersion"
                    const val APP_NAME = "$appName"
                }
            """.trimIndent())
        }
    }
    
    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir(generateBuildConfig)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
                
                // Google Admin SDK Directory API & Google Auth
                implementation(libs.google.api.services.admin.directory)
                implementation(libs.google.auth.library.oauth2.http)
                implementation(libs.google.api.client)
                implementation(libs.google.http.client.gson)
            }
        }
        
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.junit5)
                implementation(libs.junit.jupiter)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "it.madx.edttocdc.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg, 
                TargetFormat.Msi, 
                TargetFormat.Exe,
                TargetFormat.Deb
            )
            packageName = appName
            packageVersion = appVersion
            description = "Sincronizzazione Mailing List Consigli di Classe tramite EDT e Google Workspace"
            vendor = "Madx"
            
            linux {
                shortcut = true
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.png"))
            }
            windows {
                shortcut = true
                dirChooser = true
                menu = true
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.ico"))
            }
            macOS {
                bundleID = "it.madx.edttocdc"
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.icns"))
            }
        }
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "it.madx.edttocdc.MainKt"
    }
    val runtimeClasspath = configurations.getByName("desktopRuntimeClasspath")
    val fatJarNatives = configurations.getByName("fatJarNatives")
    from(runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) })
    from(fatJarNatives.map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.getByName("desktopJar") as CopySpec)
}
