package com.guardsquare.appsweep.gradle.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignedURLRequest(
    var name: String,
    var size: Long,
    var type: String
)

@JsonClass(generateAdapter = true)
data class SignedURLResponse(
    var url: String,
    var fileId: String
)

@JsonClass(generateAdapter = true)
data class CreateNewBuildRequest(
    var inputFileId: String,
    val mappingFileId: String?,
    val libraryFileId: String?,
    var tags: List<String>?,
    var commitHash: String?,
    var source: String?
)

@JsonClass(generateAdapter = true)
data class CreateNewBuildResponse(
    var details: CreateNewBuildResponseDetails,
    var message: String
)

@JsonClass(generateAdapter = true)
data class CreateNewBuildResponseDetails(
    var buildUrl: String
)
