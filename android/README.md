# Taurus — Android Port

Two APKs ship from this repo.

| APK | Purpose |
|-----|---------|
| `app/` | Companion / liquid-glass showcase. Installs standalone. |
| `xposed/` | **LSPosed hook module.** Patches Spotify Android at runtime — premium spoof, ad block, remote-config rewrites. Requires [LSPosed](https://github.com/LSPosed/LSPosed) or EdXposed. |

## How the hook works

The iOS tweak intercepts Spotify's HTTP responses using Logos / Objective-C method swizzling.
Android can't do that natively, so the hook module uses **XposedBridge** instead:

1. `TaurusXposed` is declared as the entry point in `assets/xposed_init`.
2. LSPosed loads it inside Spotify's process when Spotify starts.
3. `SpotifyHook` hooks `okhttp3.OkHttpClient$Builder.build()` and injects `TaurusInterceptor`
   into every OkHttp client Spotify creates.
4. The interceptor catches responses from `/user-customization-service/v1/customize` and
   `/bootstrap`, reads the raw protobuf body, runs `PremiumPatcher`, and rebuilds the response
   with the patched bytes — the same protobuf edits the iOS tweak applies in `Premium.m`.

## Install

1. Enable LSPosed for `com.spotify.music`.
2. Install `taurus-xposed-release-*.apk`.
3. Enable the **Taurus** module in the LSPosed app.
4. Force-stop Spotify and relaunch.

## Liquid glass

The `app/` module renders the same liquid-glass look on Android using Jetpack Compose:

- **API 31+** — real `RenderEffect` blur behind a translucent layer + specular highlight + rim light.
- **API 26–30** — frosted-fill fallback with the same highlight and rim (no blur node).

The `GlassScene` / `LiquidGlassSurface` composables in
`app/src/main/java/com/taurus/android/ui/components/LiquidGlass.kt` are the Android
equivalent of `UIGlassEffect` from iOS 26.

## Build

GitHub Actions builds both APKs on every push to `main`. See `.github/workflows/build-apk.yml`.

To build locally:

```bash
./gradlew :app:assembleDebug :xposed:assembleDebug
```
