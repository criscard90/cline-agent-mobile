# cline-agent-mobile

Android app for managing GitHub Codespaces and chatting with the Cline CLI running in a Codespace. The app provisions the bridge, publishes its port automatically, and supports OpenRouter free models with tool use.

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

Check the build status at: https://github.com/criscard90/cline-agent-mobile/actions

---

## 🔒 Security

- GitHub and OpenRouter credentials, plus local chat history, are stored in Android EncryptedSharedPreferences backed by Android Keystore.
- GitHub and OpenRouter API calls use HTTPS; the bridge connection uses WSS.

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
├── bridge/ (Node.js WebSocket bridge and automatic port setup)
│   ├── server.js
│   ├── configure-public-port.sh
│   ├── package.json
│   └── test.js
├── .devcontainer/devcontainer.json
├── .github/workflows/ (CI/CD)
│   └── workflow.yml
└── README.md
```

## ✨ Features

- GitHub PAT verification and Codespace management (list, create, start, stop, delete, open in browser).
- Chat with Cline CLI running in the selected Codespace, with streamed responses.
- A visible “Cline is processing” indicator while a response is being generated.
- OpenRouter API-key verification and model selection from the current free, tool-capable catalog.
- Markdown rendering for assistant responses, including tables.
- Encrypted local storage for credentials, selected model, and chat history.

### 💬 Chat with Cline and free OpenRouter models

The chat runs the Cline CLI in a Codespace through `bridge/server.js`. You need a GitHub PAT with access to the repository and Codespaces, plus an OpenRouter API key for AI requests. The PAT alone does not provide access to models. GitHub may charge for Codespace usage.

### 1. Configure credentials

- In Settings, enter a GitHub PAT with the `repo` and `codespace` scopes, then tap **Save and verify**. The token must be able to read and update bridge files in the selected repository and create or start Codespaces.
- Create a key at [OpenRouter](https://openrouter.ai/keys), paste it into the OpenRouter section, and tap **Verify and save**. The key is stored encrypted on the phone.

### 2. Automatic bootstrap

Tap **Start Codespace bootstrap** and choose the repository configured in Settings. The app:
1. updates `bridge/server.js`, `bridge/package.json`, `bridge/configure-public-port.sh`, and `.devcontainer/devcontainer.json` in the repository;
2. creates a Codespace if no suitable one exists, or starts an existing stopped one;
3. uses the devcontainer lifecycle to install the Cline CLI if needed, start the bridge, and automatically make port 3000 public without opening the Codespace;
4. verifies that the bridge's HTTP endpoint responds, then saves its WSS URL and starts the Android service.

If the bridge configuration changes and a replacement Codespace is required, bootstrap verifies the new bridge before stopping the previous Codespace; the old Codespace is not deleted. Organization policies may restrict public port forwarding.

### 3. Choose a model and chat

- In Chat, tap the model selector to load OpenRouter models listed as free and declaring tool support. Model availability, limits, and pricing may change.
- Send a message: the bridge starts the Cline CLI in the Codespace repository directory and streams the response. The selected model applies to new requests.
- The OpenRouter key is sent to the bridge only for a request; Cline uses a temporary configuration directory that is removed when the task ends.
- Assistant responses are rendered as Markdown, including lists, code blocks, links, and tables.
- Create multiple chats for each Codespace with the **New chat** button, then use the chat selector to switch between them. Chats and their Cline prompt context are kept separate by conversation ID and configured bridge URL, so changing Codespaces shows that Codespace's own conversations.
- Each chat's messages and active selection are stored locally in encrypted form and remain available when you close and reopen the Chat screen. Existing single-chat history is migrated automatically. Conversations are not synchronized to the cloud.

### Notes and limitations

- **Bridge security**: port 3000 is public to allow access from the phone; WebSocket requests require GitHub PAT authentication. Protect the token and do not share the bridge URL.
- **Tool approval**: Cline currently runs with automatic action approval (`--auto-approve true`). Use it only in repositories and Codespaces where you authorize Cline to edit files and run commands.
- **Multi-turn context**: for each request, the bridge starts a fresh Cline session and includes recent conversation messages in the prompt. Chat history stored on the phone is not synchronized with or restored as a Cline session in the Codespace.
- **Stop**: the Stop button attempts to terminate the running Cline process.
- The Codespace bridge requires Node.js/npm, GitHub CLI, and `setsid`; it uses the Node 18 devcontainer or a compatible image. The app connects to the bridge over WSS.

## Development and testing

On Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
npm --prefix bridge test
node --check bridge/server.js
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. To install it on an Android device connected over ADB:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📝 License

MIT License - see LICENSE file for details.