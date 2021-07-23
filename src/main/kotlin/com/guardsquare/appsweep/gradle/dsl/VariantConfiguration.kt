package com.guardsquare.appsweep.gradle.dsl

class VariantConfiguration(val name: String) {
    var tags = mutableListOf<String>()

    fun tags(vararg configuredTags: String) {
        tags.addAll(configuredTags)
    }
}
