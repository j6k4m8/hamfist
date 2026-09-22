# APK releases

Push an annotated stable version tag to build and publish both installable APKs:

```sh
git switch main
git pull --ff-only
git tag -a v0.2.0 -m "Hamfist v0.2.0"
git push origin v0.2.0
```

Use a new, increasing `vMAJOR.MINOR.PATCH` tag for every release. Suffixes and leading zeros are rejected. The tag supplies both APKs' `versionName`; `versionCode` is `major * 1000000 + minor * 1000 + patch`. Minor/patch are limited to 999 and major to 2099, keeping the code within Android's limit. `gradle.properties` supplies versions for ordinary local/debug builds; the release workflow overrides them from the tag.

The **Release** workflow tests the release-version logic and Kotlin core, builds and lints both release variants, then signs both with the same persistent certificate. Packaging verifies the signature, application ID, version, non-debuggable status, alignment and matching phone/watch certificates. Only a successful build reaches the publishing job.

The workflow creates a draft release, uploads `hamfist-phone-vX.Y.Z.apk`, `hamfist-wear-vX.Y.Z.apk` and `SHA256SUMS`, then publishes it. A failed upload leaves a draft that can be completed by rerunning the failed workflow. Published releases are never overwritten by a rerun; fixes get a new tag.

## Signing key

Repository Actions secrets:

- `HAMFIST_KEYSTORE_BASE64`: base64-encoded PKCS12 keystore containing alias `hamfist`.
- `HAMFIST_KEYSTORE_PASSWORD`: the keystore and private-key password.

The one-time local signing backup lives in the ignored `.signing/` directory (`hamfist-release.p12` and `password`), with private filesystem permissions. Keep an encrypted backup: the same key is needed for future APK updates. Do not replace the key between releases or add these files to Git. GitHub exposes secret names but cannot return their stored values.

The workflow materializes the keystore only for signing and removes that temporary file afterward. It passes the password through an environment variable, never a command-line value. Signing secrets are unavailable to the ordinary PR workflow. APKs/checksums are the only release artifacts; the publishing job receives no signing credentials.

Release installs can update earlier releases signed with this key. Existing debug builds use a different certificate and must be uninstalled once before installing a release APK. Phone and watch release APKs share the application ID and certificate required for Wear Data Layer delivery. This workflow publishes sideloadable APKs; Play Store distribution is separate.

API references: [Android APK signing](https://developer.android.com/tools/apksigner), [GitHub release creation](https://cli.github.com/manual/gh_release_create).
