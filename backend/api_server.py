import os
import base64
from flask import Flask, request, jsonify
from memory import database as db
from assistant.brain import BabyBrain

app = Flask(__name__)

# Initialize database
db.init_db()

# Initialize AI Assistant Brain
brain = BabyBrain()

@app.route("/health", methods=["GET"])
def health_check():
    """
    Checks the status of the Flask backend and detects if the llama.cpp server is reachable
    """
    import requests
    llama_connected = False
    try:
        response = requests.get("http://127.0.0.1:8080/health", timeout=2)
        if response.status_code == 200:
            llama_connected = True
    except Exception:
        pass

    return jsonify({
        "status": "healthy",
        "llama_cpp_connected": llama_connected
    })

@app.route("/chat", methods=["POST"])
def chat():
    """
    Processes chat prompts, communicates with llama.cpp, and appends to SQLite logs
    """
    data = request.get_json() or {}
    message = data.get("message")
    history = data.get("history") or []

    if not message:
        return jsonify({"error": "Missing message body parameter"}), 400

    db.save_log("API", f"Received prompt: {message[:40]}...")
    
    # Generate response
    response_text = brain.generate_response(message, history)
    
    return jsonify({
        "response": response_text
    })

@app.route("/voice", methods=["POST"])
def voice():
    """
    Processes simulated audio voice uploads (base64 audio streams) and runs transcription pipeline
    """
    data = request.get_json() or {}
    audio_base64 = data.get("audio_base64")

    if not audio_base64:
        return jsonify({"error": "Missing audio_base64 body parameter"}), 400

    # In a full voice implementation, this converts base64 wav/mp3 to text via Whisper / local STT.
    # Here we simulate the pipeline response for mock inputs:
    db.save_log("Voice", "Received base64 audio block for transcription.")
    
    transcribed_text = "Hello Baby"
    response_text = brain.generate_response(transcribed_text)

    return jsonify({
        "text": transcribed_text,
        "response": response_text
    })

@app.route("/memory/save", methods=["POST"])
def save_memory_endpoint():
    """
    Saves a persistent memory statement to SQLite
    """
    data = request.get_json() or {}
    content = data.get("content")
    type_ = data.get("type", "FACT")
    importance = data.get("importance", 3)

    if not content:
        return jsonify({"error": "Missing content parameter"}), 400

    db.save_memory(content, type_, importance)
    return jsonify({"status": "success", "message": "Memory saved successfully"})

@app.route("/memory", methods=["GET"])
def get_memories_endpoint():
    """
    Fetches all persistent memories
    """
    return jsonify(db.get_memories())

@app.route("/memory/<int:memory_id>", methods=["DELETE"])
def delete_memory_endpoint(memory_id):
    """
    Deletes a single memory entry
    """
    db.delete_memory(memory_id)
    return jsonify({"status": "success", "message": f"Memory {memory_id} deleted"})

@app.route("/models", methods=["GET"])
def get_models_info():
    """
    Lists recommended local model options for continuous scaling
    """
    return jsonify({
        "recommended_models": [
            {
                "name": "TinyLlama-1.1B-Chat-v1.0",
                "file": "tinyllama.gguf",
                "size_gb": 0.64,
                "ram_required_gb": 1.5,
                "status": "Recommended"
            },
            {
                "name": "Phi-3-mini-4k-instruct",
                "file": "phi3-mini.gguf",
                "size_gb": 2.2,
                "ram_required_gb": 4.0,
                "status": "Advanced"
            }
        ]
    })

@app.route("/settings", methods=["POST"])
def update_settings():
    """
    Saves custom server-side system settings
    """
    data = request.get_json() or {}
    conn = db.get_db_connection()
    cursor = conn.cursor()
    for key, value in data.items():
        cursor.execute(
            "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)",
            (key, str(value))
        )
    conn.commit()
    conn.close()
    return jsonify({"status": "success", "message": "Settings updated"})

if __name__ == "__main__":
    # Start flask application
    app.run(host="0.0.0.0", port=5000, debug=True)
