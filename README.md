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

### 💬 Collega la Chat a Cline — setup automatico a solo PAT

La chat parla con un **bridge server** (`bridge/server.js`) che gira dentro il tuo GitHub Codespace. L'impostazione è automatica: l'utente inserisce SOLO il PAT GitHub e l'app provvede da sola a creare/avviare il codespace, pubblicare la porta 3000 e ottenere l'URL del bridge.

### 1. In Settings, inserisci SOLO il PAT GitHub

Inserisci il token (opzionale: `ghp_…` oppure `github_pat_…`) e tocca **Salva e verifica**.
L'app verifica che il token sia valido (scope `repo`, `codespace`) e lo conserva.

### 2. Bootstrap automatico (a clic)

Toca **Avvia bootstrap Codespace**. L'app:
1. cerca un codespace per questo repository (crea se manca, avvia se fermo);
2. attende che sia davvero in esecuzione (poll, ~2 min);
3. pubblica la porta 3000 del bridge come **public**;
4. calcola l'URL `wss://<codename>-3000.app.github.dev`;
5. salva l'URL e riavvia il servizio bridge, che si collega e autentica.

### 3. Apri Chat

- Stato verde "Connesso e autenticato": puoi parlare con Cline.
- L'URL del bridge è calcolato automaticamente; se per qualche motivo ti serve un URL manuale, puoi ottenerlo dal pannello PORTS dello codespace (Settings → impostazione manuale).

### Note e limitazioni

- Il bridge (`bridge/server.js`) deve girare sulla porta 3000 nello codespace (il devcontainer lo fa all'avvio: `postStartCommand`).
- **Multi-turn**: ogni turno inietta il recente contesto della conversazione in una sessione Cline fresca (bug noto della Cline CLI dove il resume `--json --id` è rotto — cline/cline#10856). Costa qualche token in input.
- **Stop**: il pulsante stop termina il processo Cline in esecuzione.
- Android blocca `ws://` in cleartext — per test su LAN serve una network security config; `wss://` (Codespaces) funziona out of the box.

## 📝 License

MIT License - see LICENSE file for details.

# cline-agent-mobile