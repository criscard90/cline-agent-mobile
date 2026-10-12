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

## 💬 Collegare la Chat a Cline (Bridge nello Codespace)

La chat parla con un **bridge server** (`bridge/server.js`) che gira dentro il tuo GitHub Codespace. Il bridge lancia la **Cline CLI** in modalità headless (`cline --json`) e fa lo streaming della risposta dell'agente all'app via WebSocket.

### 1. Setup una-tantum dello Codespace

Il devcontainer (`.devcontainer/devcontainer.json`) installa già la Cline CLI (`npm install -g cline`) e avvia il bridge sulla **porta 3000** all'avvio. Devi solo dare a Cline un **provider LLM** nello codespace (chiave diversa dal PAT GitHub):
- esegui `cline auth` una volta nel terminale dello codespace, **oppure**
- imposta variabili d'ambiente (es. come Codespaces secrets) che il bridge inoltra a Cline:
  - `CLINE_PROVIDER` (id provider, es. `anthropic`, `openai`)
  - `CLINE_API_KEY` (la chiave API di quel provider)
  - `CLINE_MODEL` (opzionale, override del modello)
  - `CLINE_CWD` (opzionale; di default la root del repo)

### 2. Espone la porta del bridge

Nel pannello **PORTS** dello codespace, imposta la visibilità della porta **3000** su **Public**. Otterrai un URL tipo:
```
wss://<nome-codespace>-<porta>-<hash>.app.github.dev
```
> ⚠️ Una porta public è raggiungibile da chiunque abbia l'URL. Il bridge quindi **richiede autenticazione**: valida il token GitHub inviato dall'app prima di eseguire qualsiasi comando o l'agente.

### 3. Connetti dall'app

1. In **Settings**, incolla l'URL del bridge (usa `wss://…`) e salva — l'avvio del servizio bridge è automatico.
2. Assicurati che il PAT GitHub (scopes `repo`, `codespace`) sia salvato — l'app lo invia come token di auth del bridge.
3. Apri **Chat**. Lo stato diventa verde ("Connesso e autenticato") quando il bridge valida il token; l'input si sblocca e puoi parlare con Cline.

### Note e limitazioni

- **Multi-turn**: ogni turno inietta il recente contesto della conversazione in una sessione Cline fresca (aggira un bug noto della Cline CLI dove il resume `--json --id` è rotto — cline/cline#10856). Costa qualche token in input.
- **Stop**: il pulsante stop termina il processo Cline in esecuzione.
- Android blocca `ws://` in cleartext — per test su LAN serve una network security config; `wss://` (Codespaces) funziona out of the box.

## 📝 License

MIT License - see LICENSE file for details.

# cline-agent-mobile