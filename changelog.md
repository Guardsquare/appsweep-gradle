## v1.5.6 - [2023-11-30]
- Do not upload mapping file for app bundles.

## v1.5.5 - [2023-11-29]
- Fix upload tasks not working in React Native projects.

## v1.5.4 - [2023-11-21]
- Fix upload tasks not working in multi-module projects.

## v1.5.3 - [2023-11-09]
- Fix bug when AppSweep Gradle plugin has been used alongside DexGuard Gradle plugin in a multi-module project.
- Adjust input of AppSweep task to comply with latest Gradle standards.

## v1.5.2 - [2023-08-21]
- Fix bug when both the AppSweep and DexGuard Gradle plugins are used.

## v1.5.1 - [2023-07-14]
- Solve issue of protected apps and their mapping files not being uploaded properly.
- Update Gradle version to 8.1.1.

## v1.5.0 - [2023-07-04]
- Fix upload task failing in case of project using `org.gradle.unsafe.configuration-cache=true`.
- Update dependencies.

## v1.4.1 - [2023-04-06]
- If run as a GitHub action, the action's commit hash `GITHUB_SHA` is automatically used.

## v1.4.0 - [2022-10-20]
- Caching is now enabled. The task will not run if the app is unchanged. The new `cacheTask` 
  parameter can be set to false to disable this behavior and guarantee a new scan evey time the upload
  task is run.
- Update gradle version for wrapper and tests to 7.5.1.

## v1.3.0 - [2022-10-11]
- Fix bug that caused tasks not being present if DG plugin is used and not defining behavior for variants.
- Update gradle version and dependencies.
- Update tests to AGP 7.3.0.

## v1.2.0 - [2022-08-02]
- Add support for Android library projects (AARs).
- Update dependencies.

## v1.1.0 - [2022-06-20]
- Add support for uploading AAB builds.

## v1.0.0 - [2021-12-22]
- Stable release of the plugin.

## v0.1.7 - [2021-12-20]
- Do not continue task if API key is invalid.

## v0.1.6 - [2021-12-13]
- Read API-Key from environment variable (APPSWEEP_API_KEY) if not specified in the build.gradle.
- Show a nicer error message if the API key is most likely corrupt.

## v0.1.5 - [2021-10-04]
- Add support for ProGuard and R8 obfuscation mapping.
- Modify behavior to upload only the current output instead of both the obfuscated and original apk.

## v0.1.4 - [2021-08-02]
- Initial AppSweep Gradle Plugin release.
- Support for apk uploading to AppSweep and DexGuard obfuscation mapping.
