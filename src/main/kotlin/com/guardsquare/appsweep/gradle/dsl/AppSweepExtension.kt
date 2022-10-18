package com.guardsquare.appsweep.gradle.dsl

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

open class AppSweepExtension(project: Project) {
    var baseURL: String? = null
    var apiKey: String? = null
    var skipLibraryFile: Boolean = false
    var addCommitHash: Boolean = true
    var cacheTask: Boolean = true
    var commitHashCommand: String = "git rev-parse HEAD"
    val configurations: NamedDomainObjectContainer<VariantConfiguration> =
        project.container(VariantConfiguration::class.java)
}
