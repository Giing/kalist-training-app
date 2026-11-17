# Kalist training app

Local calisthenics training planner and tracker (Android).

## APK builds

- `.github/workflows/build-apk.yml` builds a debug APK on every push and publishes it
  to the rolling GitHub Release `apk-latest`.
- Stable download link (always the latest successful build):
  https://github.com/Giing/kalist-training-app/releases/download/apk-latest/kalist.apk
- `ci/debug.keystore` is a shared debug signing key (store/key password `android`,
  alias `androiddebugkey`) so successive builds install as updates over each other.
  Do not replace it, or the user must uninstall the app (losing local data) to update.

## Standing instruction from the user

After every important update (new feature, milestone of the build order, notable fix):
1. Commit and push.
2. Check the "Build APK" workflow run for that commit until it finishes.
3. If it succeeded, give the user the download link above (plus the short commit SHA).
   If it failed, read the job logs, fix the problem, and repeat.
