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
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.TaskProvider
import proguard.gradle.plugin.android.dsl.ProGuardAndroidExtension

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

            val appExtension = project.extensions.getByType(AppExtension::class.java)

            appExtension.applicationVariants.all { v ->
                // dexguard used for the variant
                // also checks if there is a dexguard task registered for the variant
                if (project.extensions.findByName("dexguard") != null && project.tasks.findByName("dexguardApk${v.name.capitalize()}") != null) {
                    // depend on dexguardApk${variant.Name}, upload protected apk
                    // upload mapping file taken from mappingDir property of the dexguard task
                    registerTasksForVariant(project,
                            extension,
                            v,
                            commitHash,
                            tasks,
                            calculateDependsOn = { variant -> "dexguardApk${variant.name.capitalize()}" },
                            calculateTags = { variant, tags -> setTags(variant, tags, "Protected", "DexGuard") },
                            calculateAppToUpload = { _ -> project.tasks.named("dexguardApk${v.name.capitalize()}").map { it.property("outputFile") }.get() as File },
                            calculateMappingFile = { variant -> Paths.get((project.tasks.named("dexguardApk${variant.name.capitalize()}").map { it.property("mappingDir") }.get() as File).path, "mapping.txt")
                                    .toAbsolutePath()
                                    .toString() }
                    )
                }
                // proguard used for the variant
                // also checks if there is a configuration set for the variant
                else if (project.extensions.findByName("proguard") != null && (project.extensions.findByName("proguard") as ProGuardAndroidExtension).configurations.any { it.name == v.name }) {
                    // depend on the assembleProvider of the variant
                    // at the moment the mapping directory path is hardcoded since the proguard plugin uses a transform that does not make the directory available
                    registerTasksForVariant(project,
                            extension,
                            v,
                            commitHash,
                            tasks,
                            calculateDependsOn = { variant -> variant.assembleProvider },
                            calculateTags = { variant, tags -> setTags(variant, tags, "Protected", "ProGuard") },
                            calculateAppToUpload = { file -> file },
                            calculateMappingFile = { variant -> Paths.get(project.buildDir.absolutePath, "outputs", "proguard", variant.name, "mapping", "mapping.txt")
                                    .toAbsolutePath()
                                    .toString() }
                    )
                }
                // R8 code optimization used
                else if (v.buildType.isMinifyEnabled){
                    registerTasksForVariant(project,
                            extension,
                            v,
                            commitHash,
                            tasks,
                            calculateDependsOn = { variant -> variant.assembleProvider },
                            calculateTags = { variant, tags -> setTags(variant, tags, "Protected", "R8") },
                            calculateAppToUpload = { file -> file },
                            calculateMappingFile = {variant -> ((project.tasks.named("minify${variant.name.capitalize()}WithR8").map{ it.property("mappingFile") }.get() as DefaultFilePropertyFactory.DefaultRegularFileVar).get().toString())}
                    )
                }
                // no optimization/obfuscation
                else
                {
                    registerTasksForVariant(project,
                            extension,
                            v,
                            commitHash,
                            tasks,
                            calculateDependsOn = { variant -> variant.assembleProvider },
                            calculateTags = { variant, tags -> setTags(variant, tags) },
                            calculateAppToUpload = { file -> file },
                            calculateMappingFile = { null }
                    )
                }
            }
        }
    }

    /**
     * Register Tasks for the considered project variant.

     * @param project the project to create the tasks for
     * @param extension the extension to register
     * @param commitHash the commit hash indicating the current commit of the project
     * @param variant the considered agp variant
     * @param createdTasks (out) the tasks created in this call
     * @param calculateDependsOn how to calculate on which tasks the newly create one should depend
     * @param calculateTags how to calculate the tags for this tasks
     * @param calculateAppToUpload how to calculate which app to upload
     * @param calculateMappingFile how to calculate which mapping file to upload
     */
    private fun registerTasksForVariant(
            project: Project,
            extension: AppSweepExtension,
            variant: ApplicationVariant,
            commitHash: String?,
            createdTasks: MutableList<TaskProvider<AppSweepTask>>,
            calculateDependsOn: (ApplicationVariant) -> Any,
            calculateTags: (ApplicationVariant, List<String>?) -> List<String>?,
            calculateAppToUpload: (File) -> File,
            calculateMappingFile: (ApplicationVariant) -> String?
    ) {
        variant.outputs.all { output ->
            val outputName = output.filters.joinToString("") {
                it.identifier.capitalize()
            } + variant.name.capitalize()

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
                extension.apiKey ?: "",
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
