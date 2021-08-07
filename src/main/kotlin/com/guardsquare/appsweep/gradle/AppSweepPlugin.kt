package com.guardsquare.appsweep.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.guardsquare.appsweep.gradle.dsl.AppSweepExtension
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.TaskProvider

class AppSweepPlugin : Plugin<Project> {

    private var isApplied = false;

    override fun apply(project: Project) {
        project.plugins.all {
            if (!isApplied) {
                val androidExtension = project.extensions.findByName("android")
                if (androidExtension != null) {

                    isApplied = true;

                    when (androidExtension) {
                        is AppExtension -> {
                            applyPluginForApp(project)
                        }
                        else -> { // e.g., LibraryExtension is not supported yet.
                            throw GradleException(
                                    "The Appsweep plugin can only be used on Android application projects."
                            )
                        }
                    }
                }

            }
        }

        project.afterEvaluate {
            if (project.extensions.findByName("android") == null) {
                project.logger.warn("The AppSweep gradle plugin was not added, since no Android App plugin was found.")
            }
        }
    }

    private fun applyPluginForApp(project: Project) {
        // Create the Appsweep extension
        val extension = project.extensions.create(APPSWEEP_EXTENSION_NAME, AppSweepExtension::class.java, project)
        project.afterEvaluate {
            val commitHash = getGitCommit(extension, project.rootProject)

            val tasks = mutableListOf<TaskProvider<AppSweepTask>>()

            // depend on assemble, upload normal apk
            registerTasksForVariants(project,
                extension,
                commitHash,
                "",
                tasks,
                calculateDependsOn = { variant -> variant.assembleProvider },
                calculateTags = { variant, tags -> setTags(variant, tags) },
                calculateAppToUpload = { file -> file },
                calculateMappingFile = { variant ->
                    Paths.get(project.buildDir.absolutePath, "outputs", "mapping", variant.name, "mapping.txt")
                    .toAbsolutePath()
                    .toString() }
            )

            if (project.extensions.findByName("dexguard") != null) {
                // depend on dexguardApk${variant.Name}, upload protected apk
                registerTasksForVariants(project,
                        extension,
                        commitHash,
                        "Protected",
                        tasks,
                        calculateDependsOn = { variant -> "dexguardApk${variant.name.capitalize()}" },
                        calculateTags = { variant, tags -> setTags(variant, tags, "Protected") },
                        calculateAppToUpload = { file -> File(file.parentFile, "${file.nameWithoutExtension}-protected.${file.extension}") },
                        calculateMappingFile = { variant ->
                            Paths.get(project.buildDir.absolutePath, "outputs", "dexguard", "mapping", "apk", variant.name, "mapping.txt")
                                .toAbsolutePath()
                                .toString() }
                )
            }
        }
    }


    /**
     * Register Tasks for all variants of this project.

     * @param project the project to create the tasks for
     * @param extension the extension to register
     * @param commitHash the commit hash indicating the current commit of the project
     * @param taskNameSuffix the registered task `uploadToAppsweep${variantName}${taskNameAppend}`
     * @param createdTasks (out) the tasks created in this call
     * @param calculateDependsOn how to calculate on which tasks the newly create one should depend
     * @param calculateTags how to calculate the tags for this tasks
     * @param calculateAppToUpload how to calculate which app to upload
     * @param calculateMappingFile how to calculate which mapping file to upload
     */
    private fun registerTasksForVariants(
        project: Project,
        extension: AppSweepExtension,
        commitHash: String?,
        taskNameSuffix: String,
        createdTasks: MutableList<TaskProvider<AppSweepTask>>,
        calculateDependsOn: (ApplicationVariant) -> Any,
        calculateTags: (ApplicationVariant, List<String>?) -> List<String>?,
        calculateAppToUpload: (File) -> File,
        calculateMappingFile: (ApplicationVariant) -> String?
    ) {

        val appExtension = project.extensions.getByType(AppExtension::class.java)
        appExtension.applicationVariants.all { variant ->
            variant.outputs.all { output ->
                val outputName = output.filters.joinToString("") {
                    it.identifier.capitalize()
                } + variant.name.capitalize() + taskNameSuffix

                project.logger.info("Registered gradle task $APPSWEEP_TASK_NAME$outputName")

                val config = parseConfigForVariant(extension, variant)
                val task = project.tasks.register(APPSWEEP_TASK_NAME + outputName, AppSweepTask::class.java) {
                    it.inputFile = calculateAppToUpload(output.outputFile)
                    it.mappingFileName = calculateMappingFile(variant)
                    it.config = config
                    it.variant = variant
                    it.gradleHomeDir = project.gradle.gradleUserHomeDir.absolutePath
                    it.commitHash = commitHash
                    it.tags = calculateTags(variant, config.tags)
                    it.dependsOn(calculateDependsOn(variant))
                    it.group = "AppSweep"
                }
                createdTasks.add(task)
            }
        }
    }

    private fun setTags(variant: ApplicationVariant, tags: List<String>?, vararg additionalTags: String): MutableList<String> {
        val outTags = mutableListOf<String>()
        if (tags == null) {
            outTags.add(variant.name.capitalize())
        } else {
            outTags.addAll(tags)
        }
        outTags.addAll(additionalTags)
        return outTags
    }

    private fun parseConfigForVariant(extension: AppSweepExtension, variant: ApplicationVariant): Configuration {
        val tags = extension.configurations.findByName(variant.name)?.tags

        return Configuration(
            extension.baseURL ?: DEFAULT_BASE_URL,
            extension.apiKey ?: throw GradleException("The AppSweep configuration block should have an apiKey specified."),
            extension.skipLibraryFile,
            tags
        )
    }

    private fun getGitCommit(extension: AppSweepExtension, rootProject: Project): String? {
        if (!extension.addCommitHash && extension.commitHashCommand.isNotEmpty()) {
            return null
        }

        rootProject.logger.info("Getting commit hash via `{}`", extension.commitHashCommand)

        val commandOutput = ByteArrayOutputStream()
        return try {
            val ret = rootProject.exec {
                it.commandLine = extension.commitHashCommand.split(" ")
                it.standardOutput = commandOutput
            }
            if (ret.exitValue != 0) { // e.g., command called wrongly
                rootProject.logger.warn("Command `${extension.commitHashCommand}` returned ${ret.exitValue}")
                null
            } else {
                commandOutput.toString().trim()
            }
        } catch (e: Exception) { // e.g. command not found / not installed
            rootProject.logger.warn(e.message) // this prints e.g. "A problem occurred starting process 'command 'asdasdasd''"
            null
        }
    }

    companion object {
        const val APPSWEEP_EXTENSION_NAME = "appsweep"
        const val APPSWEEP_TASK_NAME = "uploadToAppSweep"

        const val DEFAULT_BASE_URL = "https://appsweep.guardsquare.com"
    }
}
