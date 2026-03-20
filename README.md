# Spicy Launcher

<img src="https://i.imgur.com/xXs9THS.png">

> A custom, lightweight Minecraft: Java Edition launcher built with JavaFX — featuring Microsoft authentication, automatic version installation, Fabric support, and a sleek dark/light UI.

---

### ⚙️ How It Works

- **Microsoft OAuth Authentication**: Logs in via Microsoft's official OAuth flow and caches the token locally — no need to re-authenticate every launch.
- **Offline Mode Support**: No Microsoft account? No problem. Enter any username and play in offline mode instantly.
- **Auto Version Installer**: Automatically downloads and installs any selected Minecraft version if it's not already present, with real-time progress shown directly on the play button.
- **Fabric Loader Support**: Detects and correctly handles `fabric-loader-*` version strings, resolving asset indices automatically.
- **Asset Index Auto-Detection**: Reads `version.json` and recursively resolves `inheritsFrom` chains to find the correct asset index for any version.
- **Dark / Light Theme**: Toggle between dark and light mode — preference is saved and restored between sessions.
- **Dev Log Window**: Optionally attach a live log window to the running game process for debugging.
- **Borderless Custom UI**: Fully custom borderless window with drag-to-move support, minimize and close buttons handled manually via JavaFX.
- **Settings Persistence**: All user preferences (username, version, memory, window size, theme, etc.) are saved to a local JSON file.

---

## 📁 Setup

### 1. Requirements

| Requirement | Version |
|-------------|---------|
| Java | 17 or higher |
| JavaFX | 17+ (bundled or on module path) |
| OS | Windows (stores data in `%APPDATA%\.spicy`) |

### 2. Build

Clone the repository and build with your preferred build tool (Maven/Gradle). Make sure JavaFX is on the module path.

```bash
git clone https://github.com/yourusername/spicy-launcher.git
cd spicy-launcher
./gradlew run
```

> **Note:** If you're packaging as a fat JAR, ensure JavaFX natives are included or run with `--module-path` pointing to your JavaFX SDK.

### 3. First Launch

On first launch, the launcher will:
1. Create the `.spicy` directory under `%APPDATA%`
2. Fetch available Minecraft versions from Mojang's API
3. Prompt you to log in with Microsoft (optional)

---

### 🚀 Usage

#### Login Options

**Microsoft Account (Premium)**
- Click the **MS Auth** button
- Complete OAuth flow in the browser window that opens
- Your token is cached to `ms_auth_cache.json` — subsequent launches skip re-authentication

**Offline Mode (Cracked)**
- Leave Microsoft auth untouched
- Type any username into the username field
- A UUID is generated deterministically from your username (`Spicy:<username>`)

#### Playing

1. Select a Minecraft version from the dropdown
   - Versions already installed are highlighted in **orange**
   - Uninstalled versions are downloaded automatically on first launch
2. Hit **Login / Play**
3. Progress is shown live on the button itself (e.g. `Downloading assets... %72`)

---

### 🎛️ Settings

Accessible via the ⚙️ settings button. Options include:

| Setting | Description |
|---------|-------------|
| **Max Memory (MB)** | JVM `-Xmx` heap allocation for Minecraft |
| **Window Width / Height** | Custom game resolution (0 = default) |
| **Fullscreen** | Launch Minecraft in fullscreen mode |
| **Close Launcher on Launch** | Hides the launcher window while the game is running; restores it on exit |
| **Show Dev Log** | Opens a live console window attached to the game process |
| **Dark Theme** | Toggle dark/light UI theme |

---

### 📁 File Structure

All launcher data is stored under:
```
%APPDATA%\.spicy\
├── launch_settings.json     # User preferences
├── ms_auth_cache.json       # Cached Microsoft token
├── assets\                  # Minecraft asset objects & indexes
├── versions\
│   └── 1.21.4\
│       ├── 1.21.4.json
│       └── 1.21.4.jar
└── libraries\               # Minecraft libraries
```

---

### 🛠️ Technical Features

| Feature | Description |
|---------|-------------|
| **JavaFX FXML UI** | Declarative layout with `MainViewController` handling all logic |
| **Async Everything** | Version loading, auth, and game launch all run on background threads |
| **Token Expiry Check** | Cached MS tokens are validated on startup; expired tokens are silently cleared |
| **Fabric Version Parsing** | Strips `fabric-loader-` prefix and resolves base version for asset index lookup |
| **Recursive Asset Resolution** | Follows `inheritsFrom` in version JSONs to find the correct asset index |
| **Button Progress Bar** | A `Region` overlays the play button and grows proportionally to download progress |
| **Themed Dialogs** | Yes/No confirmation dialogs are custom-built to match the active theme |
| **Multi-Instance Guard** | Warns the user before launching a second game instance |

---

### 🏗️ Architecture Overview

```
spicy.launcher
├── ui/
│   ├── MainViewController.java    # Core UI logic, auth, launch flow
│   ├── SettingsDialog.java        # Settings popup
│   └── DevLogWindow.java          # Live game log viewer
├── core/
│   ├── MicrosoftAuthManager.java  # OAuth2 + Minecraft token flow
│   ├── GameLauncher.java          # Process builder, JVM args
│   └── VersionManager.java        # Version fetching, download, install
└── model/
    ├── DisplayVersion.java        # Version list item (id + installed flag)
    ├── LaunchContext.java         # All params passed to game process
    └── LaunchSettings.java        # JSON-serializable user preferences
```

---

### 📦 Dependencies

| Package | Purpose |
|---------|---------|
| `javafx` | UI framework (controls, FXML, scene graph) |
| `gson` | JSON serialization for settings and version manifests |
| `aiohttp` *(if applicable)* | Async HTTP for asset/version downloads |

---

### ⚠️ Disclaimer

This launcher is an independent, third-party project and is **not affiliated with Mojang or Microsoft**. Playing Minecraft via this launcher still requires a valid, purchased Minecraft: Java Edition license for online/authenticated play. The developer is not responsible for any account issues or misuse. Use responsibly.

---

### 🌹 Special Thanks
[@fantasywastaken](https://github.com/fantasywastaken) for contributions and support
