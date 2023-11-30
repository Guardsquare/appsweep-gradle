package com.guardsquare.appsweep.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.guardsquare.appsweep.gradle.dependencyanalysis.AppDependency
import com.guardsquare.appsweep.gradle.dsl.AppSweepExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.DefaultFilePropertyFactory.DefaultRegularFileVar
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths

@Suppress("unused")
class AppSweepPlugin : Plugin<Project> {

    private var isApplied = false

    override fun apply(project: Project) {
        project.plugins.all {
            if (!isApplied) {
                val androidExtension = project.extensions.findByName("android")
                if (androidExtension != null) {

                    isApplied = true

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
        val extension = project.extensions.create(
            APPSWEEP_EXTENSION_NAME,
            AppSweepExtension::class.java,
            project
        )
        project.afterEvaluate {

            val tasks = mutableListOf<TaskProvider<AppSweepTask>>()

            val variants = if (isLibrary) {
                project.extensions.getByType(LibraryExtension::class.java).libraryVariants
            } else {
                project.extensions.getByType(AppExtension::class.java).applicationVariants
            }

            variants.all { v ->
                var variantApkTaskName = v.assembleProvider.name
                val variantTags = v.name.replaceFirstChar { char -> char.uppercaseChar() }
                var variantBundleTaskName = "sign${v.name.replaceFirstChar { char -> char.uppercaseChar() }}Bundle"
                var calculateTags: (List<String>?) -> List<String>? = { getTagList(variantTags, it) }
                var calculateApkToUpload: (Task) -> File = { v.outputs.first().outputFile }
                var calculateBundleToUpload: (Task) -> File = { f -> f.outputs.files.singleFile }
                var calculateMappingFile: (Task) -> String? = { null }

                val dgVariantApkTaskName = "dexguard${if (isLibrary) "Aar" else "Apk"}${v.name.replaceFirstChar { it.uppercaseChar() }}"

                // dexguard used for the variant
                if (project.extensions.findByName("dexguard") != null && project.tasks.any { t ->
                        t.name.equals(
                            dgVariantApkTaskName
                        )
                    }) {
                    variantApkTaskName = dgVariantApkTaskName
                    variantBundleTaskName = "dexguardAab${v.name.replaceFirstChar { it.uppercaseChar() }}"
                    calculateTags = { tags -> getTagList(variantTags, tags, "Protected", "DexGuard") }
                    calculateApkToUpload = { it ->
                        val apkPath = when(val outputFile = it.property("outputFile")) {
                            is DefaultRegularFileVar -> {
                                outputFile.get().asFile
                            }

                            is File -> {
                                outputFile
                            }

                            else -> {
                                throw FileNotFoundException("Could not find the APK (type: ${outputFile!!::class.java.typeName} to upload to AppSweep. Please contact AppSweep support or open an issue in AppSweep Gradle plugin GitHub page (https://github.com/Guardsquare/appsweep-gradle).")
                            }

                        }
                        apkPath
                    }
                    calculateBundleToUpload = calculateApkToUpload
                    calculateMappingFile = { it ->
                        val mappingFilePath = when (val mappingDir = it.property("mappingDir")) {
                            is DefaultRegularFileVar -> {
                                mappingDir.get().asFile.path
                            }

                            is File -> {
                                mappingDir.path
                            }

                            else -> {
                                throw FileNotFoundException("Could not find mapping directory (type: ${mappingDir!!::class.java.typeName}. Please contact AppSweep support or open an issue in AppSweep Gradle plugin GitHub page (https://github.com/Guardsquare/appsweep-gradle).")
                            }
                        }

                        mappingFilePath.let { it1 ->
                            Paths.get(it1, "mapping.txt")
                                .toAbsolutePath()
                                .toString()
                        }
                    }
                }
                // proguard used for the variant
                // also checks if there is a configuration set for the variant
                else if (project.extensions.findByName("proguard") != null
                    && project.gradle.taskGraph.allTasks.any { currentTask -> currentTask.name == v.name }
                ) {
                    calculateTags = { tags -> getTagList(variantTags, tags, "Protected", "ProGuard") }
                    // at the moment the mapping directory path is hardcoded since the proguard plugin uses a transform that does not make the directory available
                    calculateMappingFile = {
                        Paths.get(
                            project.layout.buildDirectory.asFile.get().absolutePath,
                            "outputs",
                            "proguard",
                            v.name,
                            "mapping",
                            "mapping.txt"
                        )
                            .toAbsolutePath()
                            .toString()
                    }
                }
                // R8 code optimization used
                else if (v.buildType.isMinifyEnabled) {
                    calculateTags = { tags -> getTagList(variantTags, tags, "Protected", "R8") }
                    calculateMappingFile = {
                        ((project.tasks.named("minify${v.name.replaceFirstChar { it.uppercaseChar() }}WithR8")
                            .map { it.property("mappingFile")!! }
                            .get() as DefaultRegularFileVar).get()
                            .toString())
                    }
                }

                project.tasks.findByName(variantApkTaskName)?.let {
                    registerTasksForVariant(
                        project,
                        extension,
                        v,
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
                            tasks,
                            dependsOn = it,
                            calculateTags = calculateTags,
                            appToUpload = calculateBundleToUpload(it),
                            mappingFile = null, // app bundles already contain the mapping file
                            taskSuffix = "Bundle"
                        )
                    }
                }
            }
        }
    }

    private fun createCustomConfiguration(project: Project, toCopy: org.gradle.api.artifacts.Configuration): org.gradle.api.artifacts.Configuration {

        var configCount = 0

        while(project.configurations.findByName("customConfig${configCount}") != null) {
            configCount++
        }

        val configurationName = "customConfig${configCount}"

        val customConfiguration = project.configurations.create(configurationName)

        toCopy.attributes.keySet().forEach { a -> copyAttribute(a, toCopy, customConfiguration) }

        toCopy.allDependencies.forEach {
            if (it !is DefaultProjectDependency) {
                customConfiguration.dependencies.add(it)
            }
        }

        return customConfiguration
    }

    private fun <T> copyAttribute(attribute: Attribute<T>,
                                  toCopy: org.gradle.api.artifacts.Configuration,
                                  customConfiguration: org.gradle.api.artifacts.Configuration ) {
        customConfiguration.attributes.attribute(attribute,
            toCopy.attributes.getAttribute(attribute)!!
        )
    }


    /**
     * Register Tasks for the considered project variant.

     * @param project the project to create the tasks for
     * @param extension the extension to register
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
        createdTasks: MutableList<TaskProvider<AppSweepTask>>,
        dependsOn: Task,
        calculateTags: (List<String>?) -> List<String>?,
        appToUpload: File,
        mappingFile: String?,
        taskSuffix: String = ""
    ) {
        variant.outputs.firstOrNull()?.let { output ->
            val outputName = output.filters.joinToString("") {
                it.identifier.replaceFirstChar { id -> id.uppercaseChar() }
            } + variant.name.replaceFirstChar { it.uppercaseChar() } + taskSuffix

            project.logger.info("Registered gradle task $APPSWEEP_TASK_NAME$outputName")

            val variantName = variant.name

            val isLibrary = project.extensions.findByName("android") is LibraryExtension

            val variants = if (isLibrary) {
                project.extensions.getByType(LibraryExtension::class.java).libraryVariants
            } else {
                project.extensions.getByType(AppExtension::class.java).applicationVariants
            }
            val targetVariant = variants.first { it.name.equals(variantName) }
                ?: throw GradleException(
                    "Variant $variantName not found"
                )

            val config = parseConfigForVariant(extension, variantName)
            val task = project.tasks.register(
                APPSWEEP_TASK_NAME + outputName,
                AppSweepTask::class.java
            ) {
                it.inputFile = appToUpload
                it.mappingFileName = mappingFile
                it.config = config
                it.gradleHomeDir = project.gradle.gradleUserHomeDir.absolutePath
                it.addCommitHash = extension.addCommitHash
                it.commitHashCommand = extension.commitHashCommand
                it.tags = calculateTags(config.tags)
                it.dependsOn(dependsOn)
                it.group = "AppSweep"
                it.outputs.upToDateWhen { config.cacheTask }
                it.projectDirAbsolutePath = project.projectDir.absolutePath
                it.compileAndRuntimeDependencies = getAllDependencies(
                    compileConfiguration = createCustomConfiguration(project, targetVariant.compileConfiguration),
                    runtimeConfiguration = createCustomConfiguration(project, targetVariant.runtimeConfiguration)
                )
            }
            createdTasks.add(task)
        }
    }

    private fun getAllDependencies(
        compileConfiguration: org.gradle.api.artifacts.Configuration,
        runtimeConfiguration: org.gradle.api.artifacts.Configuration
    ): Map<String, MutableList<AppDependency>> {

        val allDependencies = HashMap<String, MutableList<AppDependency>>()

        // Get compile dependencies
        val compileDependencyType = AppDependency.DependencyType.COMPILE
        allDependencies[compileDependencyType.name] = createAppDependencySet(
            configuration = compileConfiguration,
            dependencyType = compileDependencyType
        )

        // Get runtime dependencies
        val runtimeDependencyType = AppDependency.DependencyType.RUNTIME
        allDependencies[runtimeDependencyType.name] = createAppDependencySet(
            configuration = runtimeConfiguration,
            dependencyType = runtimeDependencyType
        )

        return allDependencies
    }

    private fun createAppDependencySet(
        configuration: org.gradle.api.artifacts.Configuration,
        dependencyType: AppDependency.DependencyType
    ): MutableList<AppDependency> {

        val dependencyList = mutableListOf<AppDependency>()

        for (dependency in configuration.allDependencies) {

            if (dependency is DefaultProjectDependency) {
                continue
            }

            val dep = createAppDependency(
                dependency = dependency,
                configuration = configuration,
                dependencyType = dependencyType
            )
            dependencyList.add(dep)

        }

        return dependencyList
    }

    private fun createAppDependency(
        dependency: Dependency,
        configuration: org.gradle.api.artifacts.Configuration,
        dependencyType: AppDependency.DependencyType
    ): AppDependency {

        val isDefaultExternalModuleDependency = dependency is DefaultExternalModuleDependency
        val isDefaultSelfResolvingDependency = dependency is DefaultSelfResolvingDependency
        return AppDependency(
            group = dependency.group,
            name = dependency.name,
            specifiedVersion = dependency.version,
            dependencyType = dependencyType,
            isDefaultExternalModuleDependency = isDefaultExternalModuleDependency,
            isDefaultSelfResolvingDependency = isDefaultSelfResolvingDependency,
            files = if (isDefaultExternalModuleDependency) {
                configuration.fileCollection(dependency)
            } else if (isDefaultSelfResolvingDependency) {
                (dependency as DefaultSelfResolvingDependency).files
            } else {
                null
            }
        )
    }

    private fun getTagList(
        tagsFromVariant: String,
        tags: List<String>?,
        vararg additionalTags: String
    ): MutableList<String> {
        val outTags = mutableListOf<String>()
        if (tags == null) {
            outTags.add(tagsFromVariant)
        } else {
            outTags.addAll(tags)
        }
        outTags.addAll(additionalTags)
        return outTags
    }

    private fun parseConfigForVariant(
        extension: AppSweepExtension,
        variantName: String
    ): Configuration {
        val tags = extension.configurations.findByName(variantName)?.tags

        return Configuration(
            extension.baseURL ?: DEFAULT_BASE_URL,
            extension.apiKey ?: "",
            extension.skipLibraryFile,
            tags,
            extension.cacheTask
        )
    }

    companion object {
        const val APPSWEEP_EXTENSION_NAME = "appsweep"
        const val APPSWEEP_TASK_NAME = "uploadToAppSweep"

        const val DEFAULT_BASE_URL = "https://appsweep.guardsquare.com"
    }
}
