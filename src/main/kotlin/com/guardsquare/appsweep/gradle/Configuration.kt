package com.guardsquare.appsweep.gradle

import java.io.Serializable

class Configuration(
    val baseURL: String,
    val apiKey: String,
    val skipLibraryFile: Boolean,
    val tags: List<String>?,
    val cacheTask: Boolean
): Serializable
