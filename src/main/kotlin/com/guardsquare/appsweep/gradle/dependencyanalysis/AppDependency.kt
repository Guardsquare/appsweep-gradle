package com.guardsquare.appsweep.gradle.dependencyanalysis

import com.squareup.moshi.ToJson
import java.util.Objects

class AppDependency(val group: String?, val name: String, val specifiedVersion: String?, val dependencyType: DependencyType) {
    enum class DependencyType {
        COMPILE,
        RUNTIME
    }

    private var resolvedVersion: String? = null
    private val referencedAppLibraries = mutableSetOf<AppLibraryReference>()

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
        if (specifiedVersion != other.specifiedVersion) return false

        return true
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
    }
}
