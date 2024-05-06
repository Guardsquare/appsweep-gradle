package com.guardsquare.appsweep.gradle.network

import com.guardsquare.appsweep.gradle.ApiKeyException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import org.gradle.api.logging.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files

const val LOCAL_FOLDER_PATH: String  = "guardsquare/appsweep"
const val LOCAL_FILE_BUILD_ID: String  = "lastBuildID.txt"

class AppSweepAPIServiceV0(
    private val baseURL: String,
    private val apiKey: String,
    private val logger: Logger,
    private val buildDirectory: String
) : AppSweepAPIService {
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    override fun uploadFile(file: File, showProgress: Boolean): String? {
        val contentType = getContentTypeForFile(file)
        val requestBody = moshi.adapter(SignedURLRequest::class.java).toJson(
            SignedURLRequest(file.name, file.length(), contentType)
        )

        val signedURLRequest = Request.Builder()
            .url("$baseURL/api/v0/files/signed-url")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(signedURLRequest).execute().use { res ->
            if (!res.isSuccessful) {
                throw ApiKeyException("Failed to upload file. API key possibly incorrect.")
            }

            val responseBody = res.body
                ?: throw IOException("Failed to get signed url: response body was null")
            val signedURLResponse =
                moshi.adapter(SignedURLResponse::class.java).fromJson(responseBody.string())
                    ?: throw IOException("Failed to parse JSON response")

            val fileRequest = Request.Builder()
                .url(signedURLResponse.url)
                .header("Content-Type", contentType)
                .put(createLoggingRequestBody(contentType.toMediaType(), file, showProgress))
                .build()

            client.newCall(fileRequest).execute().use { uploadRes ->
                if (!uploadRes.isSuccessful) {
                    throw IOException("Failed to upload file: unexpected response code $uploadRes")
                }
            }

            return signedURLResponse.fileId
        }
    }

    override fun createNewBuild(createNewBuildRequest: CreateNewBuildRequest) {
        val body = moshi.adapter(CreateNewBuildRequest::class.java).toJson(createNewBuildRequest)

        val request = Request.Builder()
            .url("$baseURL/api/v0/builds")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { res ->
            if (!res.isSuccessful) {
                throw IOException("Failed to create build: unexpected response code $res")
            } else if (res.body == null) {
                throw IOException("Failed to create build: response is empty")
            } else {
                val createNewBuildResponse = moshi.adapter(CreateNewBuildResponse::class.java).fromJson(res.body!!.string())
                        ?: throw IOException("Failed to parse JSON response")
                logger.lifecycle(createNewBuildResponse.message)

                val localFolder = File("$buildDirectory/$LOCAL_FOLDER_PATH")
                localFolder.mkdirs()

                val localFilename = localFolder.resolve(LOCAL_FILE_BUILD_ID)
                val buildId = createNewBuildResponse.details.buildUrl.substringAfterLast('/')

                localFilename.printWriter().use { out ->
                    out.println(buildId)
                    logger.lifecycle("Build ID: $buildId stored in: $localFilename")
                }
                logger.lifecycle("\nYour scan results will be available at ${createNewBuildResponse.details.buildUrl}\n")
            }
        }
    }

    private fun getContentTypeForFile(file: File): String {
        return Files.probeContentType(file.toPath()) ?: "application/octet-stream"
    }

    private fun createLoggingRequestBody(contentType: MediaType, file: File, showProgress: Boolean): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType {
                return contentType
            }

            override fun contentLength(): Long {
                return file.length()
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val source = file.source()
                val buf = Buffer()
                var uploaded: Long = 0
                var lastPrinted = System.currentTimeMillis()
                var readCount: Long
                while (source.read(buf, 2048).also { readCount = it } != -1L) {
                    sink.write(buf, readCount)

                    uploaded += readCount
                    if (showProgress && System.currentTimeMillis() - lastPrinted > 1000 /* every second */) {
                        lastPrinted = System.currentTimeMillis()
                        logger.lifecycle("Uploading app for scanning, " + percentage(uploaded) + "% uploaded (" + (uploaded / 1024) + " kB / " + (contentLength() / 1024) + " kB).")
                    }
                }
            }

            private fun percentage(status: Long): Int {
                return (status * 100 / contentLength()).toInt()
            }
        }
    }
}
