# NotePilot AI 📚

**Turn lengthy study notes into simple, structured summaries.**

NotePilot AI is a study assistant that helps students understand and revise their notes more efficiently. Paste your notes, generate an AI-powered summary, and review the key concepts in clear bullet points.

## ✨ Features

- Summarizes lengthy study notes.
- Uses simple, student-friendly language.
- Organizes important concepts into bullet points.
- Frontend built with HTML, CSS, and JavaScript.
- Spring Boot backend integrated with the Hugging Face Inference API.
- Uses the open-weight `DeepSeek-V4.1-Flash` model.

## 🛠️ Tech Stack

- **Frontend:** HTML, CSS, JavaScript
- **Backend:** Java, Spring Boot, Maven
- **AI:** Hugging Face Inference API
- **Model:** `deepseek-ai/DeepSeek-V4.1-Flash`

## 🚀 Run Locally

### Prerequisites

- Java and Maven
- A Hugging Face account and access to the selected model
- VS Code or another editor for the frontend

### 1. Clone the repository

```bash
git clone https://github.com/Parshith122005/NotePilot-AI.git
cd NotePilot-AI
```

### 2. Configure your Hugging Face token

Set an environment variable named `HF_TOKEN` containing your Hugging Face access token.

Never commit your token or share it publicly.

### 3. Start the backend

Open the `backend` folder as a Maven project in your IDE. Configure `HF_TOKEN` in the run configuration, then run `NotepilotApplication.java`.

The backend runs on `http://localhost:8080` by default.

### 4. Start the frontend

Open `index.html` using VS Code Live Server. Ensure the frontend origin matches the CORS configuration in the backend.

## 🔒 Security

The Hugging Face access token is supplied through an environment variable and should not be stored in source code.

## 🎯 Project Goal

Help students spend less time rewriting notes and more time understanding and revising what they learn.

## 📌 Current Status

This project is under development. Features and setup instructions may change as development continues.
