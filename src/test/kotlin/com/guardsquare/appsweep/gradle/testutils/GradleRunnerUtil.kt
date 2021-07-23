package com.guardsquare.appsweep.gradle.testutils

import java.io.File
import java.nio.file.Files.createTempDirectory
import org.gradle.testkit.runner.GradleRunner

const val GRADLE_VERSION = "7.0"

fun createGradleRunner(projectDir: File, testKitDir: File, vararg arguments: String): GradleRunner =
    GradleRunner.create()
        .withGradleVersion(GRADLE_VERSION)
        .withTestKitDir(testKitDir)
        .withProjectDir(projectDir)
        .withArguments(*arguments)

fun createTestKitDir(): File {
    val testKitDir = createTempDirectory("testkit").toFile()
    val removeTestKitDirHook = Thread { testKitDir.deleteRecursively() }
    Runtime.getRuntime().addShutdownHook(removeTestKitDirHook)
    return testKitDir
}
