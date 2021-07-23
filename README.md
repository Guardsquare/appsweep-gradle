<p align="center">
  <br />
  <br />
  <a href="https://guardsquare.com/appsweep-mobile-application-security-testing">
    <img
      src="https://appsweep.guardsquare.com/assets/AppSweep-blue.svg"
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
  <a href="https://plugins.gradle.org/plugin/com.guardsquare.appsweep" taget="_blank">
    <img src="https://img.shields.io/gradle-plugin-portal/v/com.guardsquare.appsweep?versionPrefix=0.1&versionSuffix=4">
  </a>



  <!-- Twitter -->
  <a href="https://twitter.com/Guardsquare" taget="_blank">
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
  id "com.guardsquare.appsweep" version "0.1.4"
  // Apply other plugins here
}
```

Next, you need to configure the plugin by providing an API key for your project. 

🚀 You can create an API key in the API Keys section of your project settings.

```Groovy
appsweep {
    apiKey "gs_appsweep_SOME_API_KEY"
}
```

## Initiate the Scan

When the Gradle plugin is enabled and configured, some multiple `uploadToAppSweep*` Gradle tasks are registered.  
More specifically, one task will be registered for each build variant of your app. For example, if you want to upload your release build variant, you can run:
```bash
gradle uploadToAppSweepRelease
```
in the root folder of your app.

Moreover, if you have DexGuard configured in your project to create a protected build of your app, additional Gradle tasks will be registered for the protected builds. For example, to upload your protected release build, run:
```bash
gradle uploadToAppSweepReleaseProtected
```

To see all available AppSweep tasks, use 
```bash
gradle tasks --group=AppSweep
```

## Further Configuration

In the `appsweep`-block in your `build.gradle(.kts)` file, you can make additional configurations.

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
