package com.guardsquare.appsweep.gradle.dependencyanalysis

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AllAppDependencies(
    val dependencies: MutableSet<AppDependency>,
    val referencedLibraries: MutableSet<AppLibrary>
)

@JsonClass(generateAdapter = false)
data class AppLibraryReference(
    val group: String?,
    val name: String,
    val version: String?,
    val hash: String?
)

@JsonClass(generateAdapter = false)
data class AppLibraryJson(
    val group: String?,
    val name: String,
    val version: String?,
    val hash: String?,
    val classNames: Set<String>,
    val otherFileNames: Set<String>
)

@JsonClass(generateAdapter = false)
data class AppDependencyJson(
    val group: String?,
    val name: String,
    val specifiedVersion: String?,
    val dependencyType: AppDependency.DependencyType,
    val resolvedVersion: String?,
    val appLibraries: Set<AppLibraryReference>
)
