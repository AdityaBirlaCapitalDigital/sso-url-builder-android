# SSO URL Builder Android

Android WebView app that loads `sso-url-builder-v4.html` from app assets.

## What is customized

- Loads local HTML from: `app/src/main/assets/sso-url-builder-v4.html`
- Step 1 is converted at runtime to a single button:
  - **Auto Create UUID**
- On click, UUID is generated and auto-filled into Step 2 (`ssoToken` and sample API response)
- UI then advances to Step 3

## Run

1. Open this folder in Android Studio
2. Let Gradle sync finish
3. Run on emulator/device

## Notes

- App ID is `com.nativedemo.ssourlbuilder` so it can coexist with the original `native-demo-android` app.
- If you want to update the HTML, replace:
  - `app/src/main/assets/sso-url-builder-v4.html`
