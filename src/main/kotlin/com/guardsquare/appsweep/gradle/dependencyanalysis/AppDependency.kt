package com.guardsquare.appsweep.gradle.dependencyanalysis

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import java.io.Serializable
import java.util.Objects

class AppDependency(
    @get:Input
    @Optional
    val group: String?,
    @get:Input
    val name: String,
    @get:Input
    @Optional
    val specifiedVersion: String?,
    @get:Input
    val dependencyType: DependencyType,
    @get:Input
    val isDefaultExternalModuleDependency: Boolean = false,
    @get:Input
    val isDefaultSelfResolvingDependency: Boolean = false,
    @InputFiles
    val files: FileCollection? = null
) : Serializable {
    enum class DependencyType {
        COMPILE,
        RUNTIME
    }

    private var resolvedVersion: String? = null
    private var referencedAppLibraries = mutableSetOf<AppLibraryReference>()

    fun addReferencedAppLibraries(appLibrary: AppLibraryReference) {
        referencedAppLibraries.add(appLibrary)
    }

    override fun toString(): String {
        return if (resolvedVersion != null) {
            "$group:$name:$resolvedVersion:$dependencyType"
        } else {
            "$group:$name:$specifiedVersion:$dependencyType"
        }
    }

    /**
     * Iterate all referenced libraries to find out to which version this library is resolved.
     * In the build.gradle, the library version could be specified as e.g. [3,4), but we want to know the precise version.
     */
    fun resolveVersion() {
        for (appLibrary in referencedAppLibraries) {
            if (Objects.equals(group, appLibrary.group) &&
                Objects.equals(name, appLibrary.name)) {

                resolvedVersion = appLibrary.version
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppDependency) return false

        if (group != other.group) return false
        if (name != other.name) return false

        return specifiedVersion != other.specifiedVersion
    }

    override fun hashCode(): Int {
        var result = group?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + (specifiedVersion?.hashCode() ?: 0)
        return result
    }

    class JsonAdapter {
        @ToJson
        fun eventToJson(dep: AppDependency): AppDependencyJson {
            return AppDependencyJson(
                dep.group,
                dep.name,
                dep.specifiedVersion,
                dep.dependencyType,
                dep.resolvedVersion,
                dep.referencedAppLibraries.toSet()
            )
        }

        @FromJson
        fun jsonToEvent(appDependencyJson: AppDependencyJson): AppDependency {
            val appDependency = AppDependency(
                group = appDependencyJson.group,
                name = appDependencyJson.name,
                specifiedVersion = appDependencyJson.specifiedVersion,
                dependencyType = appDependencyJson.dependencyType
            )
            appDependency.resolvedVersion = appDependencyJson.resolvedVersion
            appDependency.referencedAppLibraries = appDependencyJson.appLibraries.toMutableSet()

            return appDependency
        }
    }
}
