package com.guardsquare.appsweep.gradle.network

import java.io.File

interface AppSweepAPIService {
    /**
     * Uploads a file and returns the file ID which can be passed to other calls that require the uploaded file.
     * @return The file ID of the uploaded file.
     */
    fun uploadFile(file: File, showProgress: Boolean): String
    fun createNewBuild(createNewBuildRequest: CreateNewBuildRequest)
}
