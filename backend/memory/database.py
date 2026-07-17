import os
import sqlite3
import time

DATABASE_PATH = "baby_assistant.db"

def get_db_connection():
    conn = sqlite3.connect(DATABASE_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db_connection()
    cursor = conn.cursor()

    # Create Conversation history table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS conversations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT NOT NULL,
        created_at REAL DEFAULT (strftime('%s', 'now')),
        is_pinned INTEGER DEFAULT 0
    )
    """)

    # Create Messages table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        conversation_id INTEGER NOT NULL,
        role TEXT NOT NULL,
        content TEXT NOT NULL,
        timestamp REAL DEFAULT (strftime('%s', 'now')),
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
    )
    """)

    # Create Memory table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS memories (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        content TEXT NOT NULL,
        type TEXT NOT NULL, -- FACT, PREFERENCE, SUMMARY
        timestamp REAL DEFAULT (strftime('%s', 'now')),
        importance INTEGER DEFAULT 3
    )
    """)

    # Create Settings table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    )
    """)

    # Create Logs table
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        tag TEXT NOT NULL,
        message TEXT NOT NULL,
        timestamp REAL DEFAULT (strftime('%s', 'now'))
    )
    """)

    conn.commit()
    conn.close()

def save_log(tag: str, message: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("INSERT INTO logs (tag, message) VALUES (?, ?)", (tag, message))
    conn.commit()
    conn.close()

def save_message(conversation_id: int, role: str, content: str):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO messages (conversation_id, role, content) VALUES (?, ?, ?)",
        (conversation_id, role, content)
    )
    conn.commit()
    conn.close()

def get_conversation_history(conversation_id: int):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC",
        (conversation_id,)
    )
    rows = cursor.fetchall()
    conn.close()
    return [{"role": row["role"], "content": row["content"]} for row in rows]

def save_memory(content: str, type: str, importance: int = 3):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO memories (content, type, importance) VALUES (?, ?, ?)",
        (content, type, importance)
    )
    conn.commit()
    conn.close()

def get_memories():
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM memories ORDER BY timestamp DESC")
    rows = cursor.fetchall()
    conn.close()
    return [{
        "id": row["id"],
        "content": row["content"],
        "type": row["type"],
        "timestamp": row["timestamp"]
    } for row in rows]

def delete_memory(memory_id: int):
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM memories WHERE id = ?", (memory_id,))
    conn.commit()
    conn.close()
