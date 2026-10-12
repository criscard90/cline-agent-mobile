# cline-agent-mobile

Android application for managing GitHub Codespaces and Cline AI from your mobile device.

## 🏗️ CI/CD Pipeline

The project includes a fully automated CI/CD pipeline using GitHub Actions.

### Workflow Overview

| Workflow | Trigger | Output |
|----------|---------|--------|
| **Build & Release** | Push to `main`, PRs, version tags (`v*.*.*`) | Debug APK, Release APK |

### Building the App

#### Debug Build (local)

```bash
# Using Android Studio or command line:
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

#### Release Build (local)

```bash
# Requires signing config in local.properties or gradle.properties
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Publishing to GitHub Releases

#### Manual Release (from Android Studio or command line)

1. Update version in `app/build.gradle`:
   ```gradle
   versionCode 2
   versionName "1.0.1"
   ```

2. Create a tag:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

3. GitHub Actions will automatically:
   - Build debug and release APKs
   - Create a GitHub Release
   - Attach APKs to the release

#### Automatic Releases

When you push a version tag (e.g., `v1.0.1`), GitHub Actions will:

1. ✅ Build debug APK → `app/build/outputs/apk/debug/app-debug.apk`
2. ✅ Build release APK → `app/build/outputs/apk/release/app-release-signed.apk`
3. ✅ Create a GitHub Release with:
   - **Debug APK**: `cline-agent-mobile-v1.0.1-debug.apk`
   - **Release APK**: `cline-agent-mobile-v1.0.1-release.apk`

### Setting Up Signing for Release Builds

To create signed release APKs, add your signing keystore:

#### Local (for local builds)

1. Create a `keystore.properties` file (add to `.gitignore`):
   ```properties
   storePassword=your_store_password
   keyPassword=your_key_password
   keyAlias=your_key_alias
   storeFile=../release/keystore.jks
   ```

2. Or add to `local.properties`:
   ```properties
   MYAPP_UPLOAD_STORE_FILE=../release/keystore.jks
   MYAPP_UPLOAD_KEY_ALIAS=your_key_alias
   MYAPP_UPLOAD_KEY_PASSWORD=your_key_password
   MYAPP_UPLOAD_STORE_PASSWORD=your_store_password
   ```

#### GitHub Actions (for automated releases)

1. Add secrets to your GitHub repository:
   - `ANDROID_KEYSTORE_BASE64`: Base64-encoded keystore file
   - `ANDROID_KEY_ALIAS`: Key alias
   - `ANDROID_KEY_PASSWORD`: Key password
   - `ANDROID_STORE_PASSWORD`: Store password

2. The workflow will automatically decrypt and sign the release APK

### Workflow Files

| File | Purpose |
|------|---------|
| `.github/workflows/workflow.yml` | Main CI/CD pipeline |
| `app/build.gradle` | Build configuration |

### Local Development Build

```bash
# Install dependencies
./gradlew dependencies

# Run debug build
./gradlew assembleDebug

# Run lint checks
./gradlew lint

# Run tests
./gradlew test

# Clean build
./gradlew clean assembleDebug
```

### CI/CD Status

Check the build status at: https://github.com/yourusername/cline-agent-mobile/actions

---

## 🔒 Security

- OAuth tokens encrypted with Android Keystore
- API keys encrypted with EncryptedSharedPreferences
- All API communication over HTTPS/WHS

## 📋 Project Structure

```
cline-agent-mobile/
├── app/
│   ├── src/main/
│   │   ├── java/com/clineagentmobile/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ClineAgentMobileApp.kt
│   │   │   ├── model/ (10 models)
│   │   │   ├── network/ (Retrofit clients)
│   │   │   ├── bridge/ (WebSocket bridge)
│   │   │   ├── storage/ (EncryptedPrefsManager)
│   │   │   ├── chat/ (Chat workspace)
│   │   │   └── ui/
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── bridge/ (Node.js bridge server)
│   ├── server.js
│   ├── package.json
│   └── .devcontainer/
│       └── devcontainer.json
├── .github/workflows/ (CI/CD)
│   └── workflow.yml
└── README.md
```

## ✨ Features

- GitHub OAuth 2.0 authentication
- Codespace management (list, create, start, stop, restart, delete)
- Chat workspace with Cline AI
- Action cards for command approval
- Diff viewer for file changes
- Encrypted API key storage
- Free tier AI providers (OpenRouter, Ollama, Gemini)

## 📝 License

MIT License - see LICENSE file for details.

# cline-agent-mobile