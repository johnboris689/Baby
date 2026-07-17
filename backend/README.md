# Baby - World-Class Private Full-Stack AI Assistant

Welcome to **Baby**, a complete offline-first AI Assistant for Android built with Kotlin, Jetpack Compose, Room, and Python.

This project is a fully functional assistant comparable to modern conversational search systems, featuring an adaptive, Siri-like glowing orb, dynamic speech recognition (STT), speech synthesis (TTS), automatic long-term memory extraction, and a provider-agnostic cognitive architecture that supports both direct cloud intelligence (Gemini Core REST API) and local private processing (llama.cpp + Flask API).

---

## Technical Architecture

```
                    ┌────────────────────────┐
                    │      Android App       │
                    │   (Jetpack Compose)    │
                    └───────────┬────────────┘
                                │
               ┌────────────────┴────────────────┐
       (Online)│                         (Offline)│
               ▼                                 ▼
   ┌───────────────────────┐            ┌─────────────────┐
   │    Gemini REST API    │            │ Flask API Server│
   │  (gemini-3.5-flash)   │            │   (Port 5000)   │
   └───────────────────────┘            └────────┬────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │ llama.cpp Server│
                                        │   (Port 8080)   │
                                        └────────┬────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │ TinyLlama-1.1B  │
                                        └─────────────────┘
```

---

## 1. Local AI Model Setup (llama.cpp & TinyLlama)

To run fully private, offline intelligence, you must host the LLM locally on your development machine using `llama.cpp`.

### Step 1: Install llama.cpp
1. Download the pre-built binaries for your platform from the [llama.cpp releases page](https://github.com/ggerganov/llama.cpp/releases).
2. Alternatively, clone and compile manually:
   ```bash
   git clone https://github.com/ggerganov/llama.git
   cd llama
   make # or use cmake on Windows
   ```

### Step 2: Download TinyLlama
1. Download a Quantized GGUF model of TinyLlama (we recommend `tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf` for balanced speed and intelligence).
2. Save the model in a local directory (e.g., `models/tinyllama.gguf`).
   * Download Link: [Hugging Face Repository](https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF)

### Step 3: Run the llama.cpp Server
Run the llama.cpp server on your host machine targeting Port `8080`:
```bash
# Unix/macOS
./llama-server -m models/tinyllama.gguf -c 2048 --port 8080

# Windows
llama-server.exe -m models/tinyllama.gguf -c 2048 --port 8080
```
Keep this terminal running. Verify it is working by opening `http://127.0.0.1:8080` in your browser.

---

## 2. Python Flask Backend Setup

The Python backend acts as an intermediary database layer, logging and orchestrating complex prompt flows, and formatting SQLite memories.

### Step 1: Install Python Dependencies
Ensure Python 3.9+ is installed, then run:
```bash
cd backend
pip install -r requirements.txt
```

### Step 2: Start the Flask Server
```bash
python api_server.py
```
This launches the backend on `http://localhost:5000`. 
* The Flask server communicates with `llama.cpp` at `http://127.0.0.1:8080/completion`.
* It stores local conversation memory, logs, and settings inside `baby_assistant.db`.

---

## 3. Android App Compilation & Installation

The Android frontend is crafted completely in native Kotlin with Jetpack Compose.

### Running in Android Studio / Gradle:
1. Open the project root folder in **Android Studio**.
2. Sync the project with Gradle files (`build.gradle.kts` and `libs.versions.toml`).
3. To build the debug APK, run:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install it on your Emulator or connected physical device:
   ```bash
   ./gradlew installDebug
   ```

### Connecting Emulator to your Host PC Server:
* By default, Android Emulators route the special loopback IP `10.0.2.2` to refer to your development machine's `localhost`.
* Therefore, the app comes pre-configured to look for the Flask server at `http://10.0.2.2:5000/` and the direct llama server at `http://10.0.2.2:8080/`. You can change these anytime in the app's **System Settings** page!

---

## 4. Production Deployment Instructions

To scale Baby to production users:

### Cloud Hosting for Backend:
1. Deploy `api_server.py` to a production service like **Google Cloud Run**, **Render**, or **AWS ECS**.
2. Run Gunicorn as the WSGI server:
   ```bash
   gunicorn -w 4 -b 0.0.0.0:5000 api_server:app
   ```
3. Set the `llama_server_url` variable in `BabyBrain` to point to a managed serverless GPU instance running your LLM.

### Android Play Store Release:
1. Generate an Upload Keystore:
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Configure release build types in `app/build.gradle.kts`.
3. Assemble the production AAB (Android App Bundle):
   ```bash
   ./gradlew bundleRelease
   ```
