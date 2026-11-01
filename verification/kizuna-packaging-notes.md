# Final packaging notes

Source commit: b1a30e6bba06a05e0f603f8bf08c0cfee71655a7
The clean Gradle build, all 71 Android JVM tests and lint passed. A post-commit incremental packaging run encountered an invalid local JDK-image transform cache. Regenerating that one disposable cache entry and running assembleRelease with configuration caching disabled succeeded. The failed log and successful final log are preserved. No app logic changed to work around the local cache.

The Windows app informational version records this commit. This local Android build tool emitted NO_SUPPORTED_VCS_FOUND in its optional version-control metadata; SOURCE_STATE.txt and the Git bundle pin the corresponding source instead.

No hosted CI, Windows Setup compilation/execution, real signed Android upgrade or physical gameplay check was performed.
