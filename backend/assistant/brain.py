import requests
import json
import logging

class BabyBrain:
    def __init__(self, llama_server_url="http://127.0.0.1:8080/completion"):
        self.llama_server_url = llama_server_url
        logging.basicConfig(level=logging.INFO)
        self.logger = logging.getLogger("BabyBrain")

    def generate_response(self, user_prompt: str, chat_history=None) -> str:
        """
        Communicates with local llama.cpp HTTP server to complete a prompt
        """
        if chat_history is None:
            chat_history = []

        # Construct a coherent dialog context for the model
        full_prompt = "System: You are Baby, a helpful, natural offline-first AI assistant.\n\n"
        
        for turn in chat_history:
            role = "User" if turn["role"] == "user" else "Assistant"
            full_prompt += f"{role}: {turn['content']}\n"

        full_prompt += f"User: {user_prompt}\nAssistant:"

        payload = {
            "prompt": full_prompt,
            "n_predict": 256,
            "temperature": 0.7,
            "stop": ["\nUser:", "\nSystem:", "<|im_end|>"]
        }

        try:
            self.logger.info(f"Sending prompt to llama.cpp server at {self.llama_server_url}...")
            response = requests.post(
                self.llama_server_url,
                json=payload,
                timeout=30
            )
            response.raise_for_status()
            result = response.json()
            
            # extract completion content (llama.cpp returns completed text inside "content")
            completed_text = result.get("content", "").strip()
            self.logger.info("Successfully generated response from llama.cpp.")
            return completed_text
            
        except requests.exceptions.RequestException as e:
            self.logger.error(f"Failed to communicate with llama.cpp: {e}")
            return (
                "Error: Llama server is unreachable. Please verify that "
                "llama.cpp is running locally (default: http://127.0.0.1:8080/completion)."
            )
