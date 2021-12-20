package com.guardsquare.appsweep.gradle

import org.gradle.api.GradleException

/**
 * Will be thrown if the key is not given or wrong.
 */
class ApiKeyException(message: String) : GradleException(message)