# MI Router Pro

Android app for managing WiFi routers (Huawei, TP-Link, D-Link, etc.) — login/config panel, connected devices scanner, and built-in speed test.

## Features
- **Home** — live dashboard: connection status, SSID, gateway IP, signal strength, device IP, link speed
- **WiFi Config** — auto-detects your router's gateway IP (defaults to Huawei ONT `192.168.100.1` if none found) or enter manually; opens the router's native admin panel in-app
- **Devices** — scans your local subnet and lists connected devices
- **Speed Test** — real download/upload/ping test with animated gauge
- **Settings** — clear saved router data, app info

## Package
`com.mi.routermanagerpro`

## Building locally
```
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
Push to `main` (or run manually via workflow_dispatch) to auto-build debug + release APKs and publish them as a GitHub Release with a tag like `v1.0.<run_number>`.

## Notes
- Release APK is **unsigned** — sign it before publishing to Play Store.
- The router admin panel uses a WebView pointed at the router's local IP, so it works with any brand's web-based admin login (Huawei, TP-Link, etc.) rather than a single hardcoded API.
