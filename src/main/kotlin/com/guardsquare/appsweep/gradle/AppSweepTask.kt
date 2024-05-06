package com.guardsquare.appsweep.gradle

import com.guardsquare.appsweep.gradle.dependencyanalysis.AllAppDependencies
import com.guardsquare.appsweep.gradle.dependencyanalysis.AppDependency
import com.guardsquare.appsweep.gradle.dependencyanalysis.AppLibrary
import com.guardsquare.appsweep.gradle.network.AppSweepAPIServiceV0
import com.guardsquare.appsweep.gradle.network.CreateNewBuildRequest
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.File.createTempFile
import java.util.regex.Pattern
import javax.inject.Inject

val LIBRARY_PATH_PATTERN: Pattern =
    Pattern.compile(".*?(?<group>[^/]+)/(?<name>[^/]+)/(?<version>[^/]+)/(?<hash>[^/]+)/(?<filename>[^/]+)\$")


abstract class AppSweepTask : DefaultTask() {

    @get:Input
    @Optional
    var tags: List<String>? = null

    @get:Input
    @Optional
    var mappingFileName: String? = null

    @get:Input
    var addCommitHash: Boolean = false

    @get:Input
    lateinit var commitHashCommand: String

    @get:Inject
    abstract val executor: ExecOperations

    @get:Input
    lateinit var gradleHomeDir: String

    @get:InputFile
    lateinit var inputFile: File

    @get:Input
    lateinit var config: Configuration

    @get:Nested
    lateinit var compileAndRuntimeDependencies: Map<String, MutableList<AppDependency>>

    @get:Input
    lateinit var projectDirAbsolutePath: String

    @get:Input
    lateinit var projectBuildDirectory: String

    @TaskAction
    fun uploadFile() {

        var libraryMapping: File? = null
        val commitHash = getGitCommit(addCommitHash = addCommitHash, commitHashCommand = commitHashCommand)

        if (!config.skipLibraryFile) {

            val allAppDependencies = AllAppDependencies(mutableSetOf(), mutableSetOf())
            addDependencies(
                allAppDependencies,
                compileAndRuntimeDependencies["COMPILE"]!!,
                AppDependency.DependencyType.COMPILE
            )
            addDependencies(
                allAppDependencies,
                compileAndRuntimeDependencies["RUNTIME"]!!,
                AppDependency.DependencyType.RUNTIME
            )

            val moshi = Moshi.Builder()
                .add(AppDependency.JsonAdapter())
                .add(AppLibrary.JsonAdapter())
                .addLast(KotlinJsonAdapterFactory()).build()

            libraryMapping = createTempFile("libraryMapping", ".json")
            libraryMapping.deleteOnExit()

            // convert to a json-file
            libraryMapping.sink().use { sink ->
                JsonWriter.of(sink.buffer()).use {
                    moshi.adapter(AllAppDependencies::class.java).toJson(it, allAppDependencies)
                }
            }
        }

        val apiKey = when {
            config.apiKey.isNotEmpty() -> {
                config.apiKey
            }

            System.getenv("APPSWEEP_API_KEY") != null -> {
                System.getenv("APPSWEEP_API_KEY")
            }

            else -> {
                throw ApiKeyException("No API key set. Either set the APPSWEEP_API_KEY environment variable or apiKey in the appsweep block")
            }
        }

        // Let's skip the network interaction during tests.
        if (java.lang.Boolean.getBoolean("appsweep.test")) {
            return
        }

        val service = AppSweepAPIServiceV0(config.baseURL, apiKey, logger, projectBuildDirectory)

        val dependencyJsonId: String? = if (libraryMapping != null) {
            // Upload the library mapping file, do not show progress for this
            logger.lifecycle("Uploading library information.")
            val uploadFile =
                service.uploadFile(libraryMapping, false) ?: return // null indicates an error
            uploadFile
        } else {
            null
        }

        // if mapping file is set, upload this as well
        var mappingFileId: String? = null
        if (mappingFileName != null) {
            // upload mapping file
            val mappingFile = File(mappingFileName!!)
            if (mappingFile.exists()) {
                logger.lifecycle("Uploading mapping file ${mappingFile.absolutePath}")
                mappingFileId = service.uploadFile(mappingFile, false)
            } else {
                logger.warn("Could not find mapping file $mappingFileName")
            }
        }

        // Upload the built apk file, also showing the progress
        val fileId = service.uploadFile(inputFile, true) ?: return // null indicates an error

        // Create the new build with this file.
        service.createNewBuild(
            CreateNewBuildRequest(
                fileId,
                mappingFileId,
                dependencyJsonId,
                tags,
                commitHash,
                "gradle"
            )
        )
    }

    /**
     * Adds all dependencies from the given configuration.
     */
    private fun addDependencies(
        dependencies: AllAppDependencies,
        allDependencies: List<AppDependency>,
        dependencyType: AppDependency.DependencyType
    ) {
        var count = 0
        for (dependency in allDependencies) {
            if (dependency.isDefaultExternalModuleDependency) {
                val appDependency = AppDependency(
                    group = dependency.group,
                    name = dependency.name,
                    specifiedVersion = dependency.specifiedVersion,
                    dependencyType = dependency.dependencyType
                )

                if (dependencies.dependencies.contains(appDependency)) {
                    // skipping previously analyzed library
                    continue
                }
                count++

                for (file in dependency.files!!) {

                    val pathMatcher = LIBRARY_PATH_PATTERN.matcher(file.absolutePath)

                    val appLibrary = if (pathMatcher.matches()) {
                        AppLibrary(
                            pathMatcher.group("group"),
                            pathMatcher.group("name"),
                            pathMatcher.group("version"),
                            pathMatcher.group("hash")
                        )
                    } else { // does not match our expected format, just use the file name as name
                        AppLibrary(
                            file.absolutePath.replace( // strip off the user/home dir
                                gradleHomeDir,
                                ""
                            )
                        )
                    }

                    appLibrary.getInformationFromZip(file)
                    appDependency.addReferencedAppLibraries(appLibrary.toReference())
                    dependencies.referencedLibraries.add(appLibrary)
                }
                appDependency.resolveVersion()

                dependencies.dependencies.add(appDependency)
            } else if (dependency.isDefaultSelfResolvingDependency) { // referenced jar file
                for (file in dependency.files!!) {

                    // strip off project directory. The library will be referenced e.g. as `lib/somelib.jar`
                    val library = file.absolutePath.replace(
                        projectDirAbsolutePath + File.separator,
                        ""
                    )

                    val appDependency = AppDependency(
                        null,
                        library,
                        null,
                        dependency.dependencyType
                    )

                    if (dependencies.dependencies.contains(appDependency)) {
                        // skip, if previously analyzed
                        continue
                    }
                    count++

                    val appLibrary = AppLibrary(library)
                    appLibrary.getInformationFromZip(file)
                    appDependency.addReferencedAppLibraries(appLibrary.toReference())
                    dependencies.referencedLibraries.add(appLibrary)
                    dependencies.dependencies.add(appDependency)
                }
            }
        }
        logger.info("Analyzed $count $dependencyType dependencies.")
    }

    private fun getGitCommit(addCommitHash: Boolean, commitHashCommand: String): String? {
        if (!addCommitHash && commitHashCommand.isNotEmpty()) {
            return null
        }

        logger.info("Getting commit hash via `{}`", commitHashCommand)

        val commandOutput = ByteArrayOutputStream()
        return try {
            val ret = executor.exec {
                it.commandLine = commitHashCommand.split(" ")
                it.standardOutput = commandOutput
            }
            if (ret.exitValue != 0) { // e.g., command called wrongly
                logger.warn("Command `${commitHashCommand}` returned ${ret.exitValue}")
                null
            } else {
                commandOutput.toString().trim()
            }
        } catch (e: Exception) { // e.g. command not found / not installed
            logger.warn(e.message) // this prints e.g. "A problem occurred starting process 'command 'asdasdasd''"
            null
        }
    }
}
