# Third-party notices

NAS Watchdog’s project code is distributed under [MIT](LICENSE). It includes or builds upon the following third-party software. These notices concern the bundled assets, not endorsement by their authors.

## uPlot

The embedded web dashboard vendors uPlot JavaScript and CSS under `watchdog/ui/vendor/`.

- Project: <https://github.com/leeoniya/uPlot>
- Package: `uplot`
- License: MIT
- Full notice: [`watchdog/ui/vendor/uPlot.LICENSE.txt`](watchdog/ui/vendor/uPlot.LICENSE.txt)

The resolved package version is recorded in `watchdog/package-lock.json`.

## Geist fonts

The Android app bundles Geist variable fonts under `android/app/src/main/res/font/`.

- Project: <https://github.com/vercel/geist-font>
- License: SIL Open Font License 1.1
- Full notice: [`android/app/src/main/assets/fonts/OFL.txt`](android/app/src/main/assets/fonts/OFL.txt)

## Build and runtime dependencies

The software also uses Go, Alpine Linux, Docker, Tailwind CSS, daisyUI, AndroidX, Jetpack Compose/Material, Kotlin, WorkManager, JUnit, and `org.json`. Exact direct versions and package metadata are declared in the Docker/Android/npm build files and lockfile; normal dependency resolution retains each upstream package’s own licensing metadata. No dependency source trees (`node_modules`, Gradle caches, Android build artifacts) are committed to this repository.
