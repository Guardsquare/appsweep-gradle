package com.guardsquare.appsweep.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.guardsquare.appsweep.gradle.dsl.AppSweepExtension
import org.gradle.api.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import org.gradle.api.internal.file.DefaultFilePropertyFactory
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
                            applyPluginForApp(project, false)
                        }
                        is LibraryExtension -> {
                            applyPluginForApp(project, true)
                        }
                        else -> {
                            throw GradleException(
                                    "The Appsweep plugin can only be used on Android application or library projects."
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

    private fun applyPluginForApp(project: Project, isLibrary: Boolean) {
        // Create the Appsweep extension
        val extension = project.extensions.create(APPSWEEP_EXTENSION_NAME, AppSweepExtension::class.java, project)
        project.afterEvaluate {
            val commitHash = getGitCommit(extension, project.rootProject)

            val tasks = mutableListOf<TaskProvider<AppSweepTask>>()

            val variants = if (isLibrary) {
                project.extensions.getByType(LibraryExtension::class.java).libraryVariants
            } else {
                project.extensions.getByType(AppExtension::class.java).applicationVariants
            }

            variants.all { v ->
                var variantApkTaskName = v.assembleProvider.name
                var variantBundleTaskName = "sign${v.name.capitalize()}Bundle"
                var calculateTags: (List<String>?) -> List<String>? = { setTags(v, it) }
                var calculateApkToUpload: (Task) -> File = { v.outputs.first().outputFile }
                var calculateBundleToUpload: (Task) -> File = { it.outputs.files.singleFile }
                var calculateMappingFile: (Task) -> String? = { null }

                val dgVariantApkTaskName = "dexguard${if (isLibrary) "Aar" else "Apk"}${v.name.capitalize()}"
                // dexguard used for the variant
                if (project.extensions.findByName("dexguard") != null && project.tasks.any { t -> t.name.equals(dgVariantApkTaskName) }) {
                    variantApkTaskName = dgVariantApkTaskName
                    variantBundleTaskName = "dexguardAab${v.name.capitalize()}"
                    calculateTags = { tags -> setTags(v, tags, "Protected", "DexGuard") }
                    calculateApkToUpload = { it.property("outputFile") as File }
                    calculateBundleToUpload = calculateApkToUpload
                    calculateMappingFile = {
                        Paths.get((it.property("mappingDir") as File).path, "mapping.txt")
                            .toAbsolutePath()
                            .toString()
                    }
                }
                // proguard used for the variant
                // also checks if there is a configuration set for the variant
                else if (project.extensions.findByName("proguard") != null && (project.extensions.findByName("proguard") as ProGuardAndroidExtension).configurations.any { it.name == v.name }) {
                    calculateTags  = { tags -> setTags(v, tags, "Protected", "ProGuard") }
                    // at the moment the mapping directory path is hardcoded since the proguard plugin uses a transform that does not make the directory available
                    calculateMappingFile = {
                        Paths.get(project.buildDir.absolutePath, "outputs", "proguard", v.name, "mapping", "mapping.txt")
                            .toAbsolutePath()
                            .toString()
                    }
                }
                // R8 code optimization used
                else if (v.buildType.isMinifyEnabled){
                    calculateTags = { tags -> setTags(v, tags, "Protected", "R8") }
                    calculateMappingFile = {
                        ((project.tasks.named("minify${v.name.capitalize()}WithR8").map { it.property("mappingFile") }.get() as DefaultFilePropertyFactory.DefaultRegularFileVar).get().toString())
                    }
                }

                project.tasks.findByName(variantApkTaskName)?.let {
                    registerTasksForVariant(
                        project,
                        extension,
                        v,
                        commitHash,
                        tasks,
                        dependsOn = it,
                        calculateTags = calculateTags,
                        appToUpload = calculateApkToUpload(it),
                        mappingFile = calculateMappingFile(it)
                    )
                }

                if (!isLibrary) {
                    // AABs are only relevant for applications, not libraries
                    project.tasks.findByName(variantBundleTaskName)?.let {
                        registerTasksForVariant(
                            project,
                            extension,
                            v,
                            commitHash,
                            tasks,
                            dependsOn = it,
                            calculateTags = calculateTags,
                            appToUpload = calculateBundleToUpload(it),
                            mappingFile = calculateMappingFile(it),
                            taskSuffix = "Bundle"
                        )
                    }
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
     * @param dependsOn the task on which the newly created one should depend
     * @param calculateTags how to calculate the tags for this tasks
     * @param appToUpload which app file to upload
     * @param mappingFile which mapping file to upload
     * @param taskSuffix suffix added to the task name for special cases (e.g. AABs)
     */
    private fun registerTasksForVariant(
        project: Project,
        extension: AppSweepExtension,
        variant: BaseVariant,
        commitHash: String?,
        createdTasks: MutableList<TaskProvider<AppSweepTask>>,
        dependsOn: Task,
        calculateTags: (List<String>?) -> List<String>?,
        appToUpload: File,
        mappingFile: String?,
        taskSuffix: String = ""
    ) {
        variant.outputs.firstOrNull()?.let { output ->
            val outputName = output.filters.joinToString("") {
                it.identifier.capitalize()
            } + variant.name.capitalize() + taskSuffix

            project.logger.info("Registered gradle task $APPSWEEP_TASK_NAME$outputName")

            val config = parseConfigForVariant(extension, variant)
            val task = project.tasks.register(APPSWEEP_TASK_NAME + outputName, AppSweepTask::class.java) {
                it.inputFile = appToUpload
                it.mappingFileName = mappingFile
                it.config = config
                it.variant = variant
                it.gradleHomeDir = project.gradle.gradleUserHomeDir.absolutePath
                it.commitHash = commitHash
                it.tags = calculateTags(config.tags)
                it.dependsOn(dependsOn)
                it.group = "AppSweep"
            }
            createdTasks.add(task)
        }
    }

    private fun setTags(variant: BaseVariant, tags: List<String>?, vararg additionalTags: String): MutableList<String> {
        val outTags = mutableListOf<String>()
        if (tags == null) {
            outTags.add(variant.name.capitalize())
        } else {
            outTags.addAll(tags)
        }
        outTags.addAll(additionalTags)
        return outTags
    }

    private fun parseConfigForVariant(extension: AppSweepExtension, variant: BaseVariant): Configuration {
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
