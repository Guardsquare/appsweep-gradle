<p align="center">
  <br />
  <br />
  <a href="https://guardsquare.com/appsweep-mobile-application-security-testing">
    <img
      src="https://appsweep.guardsquare.com/AppSweep-blue.svg"
      alt="AppSweep" width="400">
  </a>
</p>


<h4 align="center">Gradle Plugin for Continuous Integration of AppSweep App Testing.</h4>

<!-- Badges -->
<p align="center">
  <!-- License -->
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/guardsquare/appsweep-gradle-plugin">
  </a>

  <!-- Version -->
  <a href="https://plugins.gradle.org/plugin/com.guardsquare.appsweep" target="_blank">
    <img src="https://img.shields.io/gradle-plugin-portal/v/com.guardsquare.appsweep">
  </a>



  <!-- Twitter -->
  <a href="https://twitter.com/Guardsquare" target="_blank">
    <img src="https://img.shields.io/twitter/follow/guardsquare?style=social">
  </a>

</p>

<br />
<p align="center">
  <a href="#configuring-the-plugin"><b>Configuring the Plugin</b></a> •
  <a href="#initiate-the-scan"><b>Initiate the Scan</b></a> •
  <a href="#further-configuration"><b>Further Configuration</b></a> 
</p>
<br />

## Configuring the Plugin

The AppSweep plugin is published in the Gradle Public Repository, and can be easily added to your Android project by adding

```Groovy
plugins {
  // Keep your other plugins here
  id "com.guardsquare.appsweep" version "latest.release"
}
```

Important: Appsweep must run after the Android and Dexguard plugins. Please add the Appsweep plugin below Android and Dexguard in the plugins section.


Note: the dynamic version `latest.release` requires at least Gradle 7. If you want to build with an older Gradle version, you need to specify a version number.

Next, you need to configure the plugin by providing an API key for your project. 

### Use AppSweep in Multi-Module projects

To use AppSweep Gradle plugin in a multi-module project, you need to specify the build configuration for dependent modules of your project. For example, consider the case that your project has two modules, `app` and `common-lib`. In case `app` is using `common-lib` you might have a dependency like this in `build.gradle(.kts)` file of `app` module
```kotlin
implementation(":common-lib")
```
You need to change this dependency as follows:
```kotlin
implementation(path: ":common-lib", configuration: "default")
```

### Creating an API Key
🚀 You can create an API key directly in AppSweep. To do so, you need to visit 
[AppSweep](https://appsweep.guardsquare.com), go to the project you want to create 
the key for, go to the settings of the project and create the key in the API section.

This API key can then either be stored in the environment variable `APPSWEEP_API_KEY`, or by adding a <a href="#further-configuration">appsweep block</a> to your `app/build.gradle`.

## Initiate the Scan

When the Gradle plugin is enabled and configured, some multiple `uploadToAppSweep*` Gradle tasks are registered.  
More specifically, one task will be registered for each build variant of your app. For example, if you want to upload your release build variant, you can run:
```bash
gradle uploadToAppSweepRelease
```
in the root folder of your app.

Moreover, if you have obfuscation enabled for a specific build variant, the plugin will pick up the obfuscation mapping file and upload that alongside the app.

To see all available AppSweep tasks, use 
```bash
gradle tasks --group=AppSweep
```

## Further Configuration

In the `appsweep`-block in your `app/build.gradle(.kts)` file, you can make additional configurations.

### API key

Instead of using the environment variable for the API key, you can also specify it in the `appsweep`-block:

```Groovy
appsweep {
    apiKey "gs_appsweep_SOME_API_KEY"
}
```

### Tags

By default, the Gradle plugin will tag each uploaded build with the variant name (e.g. `Debug` or `Release`). Additionally it will add a `Protected` tag for builds uploaded using the `uploadToAppSweep{variant}Protected` tasks. You can override this behavior and set your own tags:

```Groovy
appsweep {
    apiKey "gs_appsweep_SOME_API_KEY"
    configurations {
        release {
            tags "Public"
        }
    }
}
```

This will tag all builds of the release variant with `Public`.

### Commit hash

By default, the Gradle plugin will keep track of the current commit hash. This will then be displayed along with your build results so you can easily identify which version was analysed. By default the command `git rev-parse HEAD` is used to obtain this commit hash.

If you don't want to keep track of the commit hash, you can turn off this feature by specifying the `addCommitHash` option:
```Groovy
appsweep {
    apiKey "gs_appsweep_SOME_API_KEY"
    addCommitHash false
}
```

You can also use an alternative command to retrieve the commit hash by overriding the `commitHashCommand` option:
```Groovy
appsweep {
    apiKey "gs_appsweep_SOME_API_KEY"
    commitHashCommand "hg id -i"
}
```

The output of the command is attached to the newly created build, and will be shown in the results to identify that specific commit.

### Task caching

By default, the upload tasks are cached and won't run if the app is unchanged.

If this is not the desired behavior you can disable the caching and guarantee the creation of a new scan everytime an upload task 
is run (Android Studio might show a warning in this case, but it can be ignored):
```Groovy
appsweep {
    apiKey "gs_appsweep_SOME_API_KEY"
    cacheTask false
}
```
