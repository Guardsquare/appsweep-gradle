package com.guardsquare.appsweep.gradle

class Configuration(
    val baseURL: String,
    val apiKey: String,
    val skipLibraryFile: Boolean,
    val tags: List<String>?
)
