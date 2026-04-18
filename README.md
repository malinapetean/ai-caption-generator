# 📸 AI Caption Generator — Codex Instructions

## 🧠 Project Purpose

Build a full-stack application that:

- takes an image as input
- extracts semantic concepts using CLIP
- builds a prompt based on style
- generates a creative caption using an LLM
- returns structured output to the frontend

This is a multimodal pipeline (vision + language).

## ⚙️ Functional Requirements

### Core Flow

UPLOAD IMAGE → Extract concepts (CLIP) → Build prompt → Generate caption (LLM) → Return result

## API Endpoint

POST /api/generate-caption

GET /api/health/llm

### Input (multipart/form-data)
- image: image file
- style: string

### Allowed styles
- poetic
- travel
- casual
- luxury
- minimalist

### Output (JSON)

{
  "concepts": ["mountains", "sunset", "lake"],
  "prompt": "string",
  "caption": "string"
}

Invalid styles return `400`.
LLM connection/configuration failures return `502`.

---

## 🧱 System Architecture

### Backend (FastAPI)
- image upload
- CLIP inference
- prompt generation
- LLM call
- response

### Frontend (React)
- upload UI
- style selection
- display results

---

## 📁 Project Structure

backend/
├── app/
│   ├── main.py
│   ├── routes/
│   │   └── caption.py
│   ├── services/
│   │   ├── clip_service.py
│   │   ├── prompt_service.py
│   │   └── llm_service.py
│   └── models/

frontend/
├── src/
│   ├── App.jsx

---

## 🔧 Backend Responsibilities

### CLIP Service
- input: image
- output: concepts list

### Prompt Service
- input: concepts + style
- output: prompt string

### LLM Service
- input: prompt
- output: caption

---

## 🧠 Prompt Engineering

Prompts must:
- include detected concepts
- enforce style
- avoid generic descriptions

Example:

Write a short poetic Instagram caption.
Use vivid imagery and emotional tone.
Concepts: mountains, sunset, lake.
Return only the caption.

---

## 🚫 Constraints

- no model training
- no database (initially)
- local execution

---

## 🛠️ Run Instructions

### Backend

cd backend
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload

### LLM configuration

By default, the backend calls local Ollama:

```env
LLM_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
```

For an OpenAI-compatible API, set:

```env
LLM_PROVIDER=api
LLM_API_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=your_api_key
LLM_API_MODEL=your_model
```

Successful caption generations are saved locally to:

```text
backend/data/generated_examples.json
```

### Frontend

cd frontend
npm install
npm run dev

The frontend calls:

```text
http://127.0.0.1:8000/api
```

Override it with:

```env
VITE_API_BASE_URL=http://127.0.0.1:8000
```

---

## 🎯 Success Criteria

- upload image
- receive concepts
- generate prompt
- generate caption
- display result

---

## 📌 Notes

- keep services modular
- do not mix logic in routes
- focus on prompt experimentation
