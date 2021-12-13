package com.guardsquare.appsweep.gradle

import com.guardsquare.appsweep.gradle.testutils.AndroidProject
import com.guardsquare.appsweep.gradle.testutils.applicationModule
import com.guardsquare.appsweep.gradle.testutils.createGradleRunner
import com.guardsquare.appsweep.gradle.testutils.createTestKitDir
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testkit.runner.TaskOutcome.SUCCESS

class AppSweepTaskTest : FreeSpec({
    val testKitDir = createTestKitDir()

    "Given an application project with an 'appsweep' block but no API key" - {
        val project = AndroidProject().apply {
            addModule(
                applicationModule(
                    "app",
                    buildDotGradle = """
                        plugins {
                            id 'com.android.application'
                            id 'com.guardsquare.appsweep'
                        }

                        android {
                            compileSdkVersion 29
                            defaultConfig {
                                targetSdkVersion 29
                                minSdkVersion 14
                                versionCode 1
                            }
                            buildTypes {
                                release {}
                                debug {}
                            }
                        }

                        appsweep {
                        }
                    """
                )
            )
        }.create()

        "When the tasks 'clean' and 'assemble' are run" - {
            val result = createGradleRunner(project.rootDir, testKitDir, "clean", "uploadToAppSweepDebug", "--info").build()

            "APPSWEEP_API_KEY environment variable must not be set for this test" {
                System.getenv("APPSWEEP_API_KEY") shouldBe null
            }

            "Then the build should fail" {
                result.output shouldContain "No API key set"
            }
        }
    }

    "Given an application project with an 'appsweep' block and an API key, unordered" - {
        val project = AndroidProject().apply {
            addModule(
                applicationModule(
                    "app",
                    buildDotGradle = """
                        plugins {
                            id 'com.android.application' apply false
                            id 'com.guardsquare.appsweep' apply false
                        }
                        
                        apply plugin: 'com.guardsquare.appsweep'
                        apply plugin: 'com.android.application'
                        
                        android {
                            compileSdkVersion 29
                            defaultConfig {
                                targetSdkVersion 29
                                minSdkVersion 14
                                versionCode 1
                            }
                            buildTypes {
                                release {}
                                debug {}
                            }
                        }
                        
                        dependencies
                        {
                            implementation 'com.google.protobuf:protobuf-java:+'
                            runtimeOnly 'com.android.support:appcompat-v7:28.0.0-rc01'
                        }
                        
                        appsweep {
                            apiKey "gs_mast_VP44ttB_5zO0QbhhLBJc31U2fWFjMnv3CnqfCPDl"
                            addCommitHash false
                        }
                    """
                )
            )
        }.create()

        "When the tasks 'clean' and 'assemble' are run" - {
            val result = createGradleRunner(project.rootDir, testKitDir, "clean", "assemble").build()

            "Then the tasks should run successfully" {
                result.task(":app:assemble")?.outcome shouldBe SUCCESS
                result.task(":app:assembleRelease")?.outcome shouldBe SUCCESS
                result.task(":app:assembleDebug")?.outcome shouldBe SUCCESS
            }

            "Then the release and debug apks are built" {
                File(project.rootDir, "app/build/outputs/apk/release/app-release-unsigned.apk").shouldExist()
                File(project.rootDir, "app/build/outputs/apk/debug/app-debug.apk").shouldExist()
            }
        }

        "When the tasks 'clean' and 'uploadAppToAppSweep' are run" - {
            val result = createGradleRunner(project.rootDir, testKitDir, "clean", "uploadToAppSweepDebug", "uploadToAppSweepRelease", "--info").build()

            "Then the tasks should run successfully" {
                result.task(":app:uploadToAppSweepRelease")?.outcome shouldBe SUCCESS
                result.task(":app:uploadToAppSweepDebug")?.outcome shouldBe SUCCESS

                result.task(":app:assembleRelease")?.outcome shouldBe SUCCESS
                result.task(":app:assembleDebug")?.outcome shouldBe SUCCESS

                result.output shouldContain "Analyzed 1 COMPILE dependencies."
                result.output shouldContain "Analyzed 1 RUNTIME dependencies."
            }

            "Then the release and debug apks are built" {
                File(project.rootDir, "app/build/outputs/apk/release/app-release-unsigned.apk").shouldExist()
                File(project.rootDir, "app/build/outputs/apk/debug/app-debug.apk").shouldExist()
            }
        }
    }
})
