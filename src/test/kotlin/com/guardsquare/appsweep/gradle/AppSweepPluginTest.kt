package com.guardsquare.appsweep.gradle

import com.guardsquare.appsweep.gradle.testutils.AndroidProject
import com.guardsquare.appsweep.gradle.testutils.applicationModule
import com.guardsquare.appsweep.gradle.testutils.createGradleRunner
import com.guardsquare.appsweep.gradle.testutils.createTestKitDir
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain

class AppSweepPluginTest : FreeSpec({
    val testKitDir = createTestKitDir()

    "Given a project without the Android Gradle plugin" - {
        val project = AndroidProject().apply {
            addModule(
                applicationModule(
                    "app", buildDotGradle = """
                    plugins {
                        id 'com.guardsquare.appsweep'
                    }
                    """.trimIndent()
                )
            )
        }.create()

        "When the project is evaluated" - {
            val result = createGradleRunner(project.rootDir, testKitDir).build()

            "Then the build should fail" {
                result.output shouldContain "The AppSweep gradle plugin was not added, since no Android App plugin was found."
            }
        }
    }
})
