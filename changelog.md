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