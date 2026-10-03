# NotePilot AI

**Turn your notes into knowledge.**

NotePilot AI is a study assistant. Paste your notes and generate a structured revision summary, a five-question multiple-choice quiz, and eight interactive flashcards. The AI is called from a Spring Boot backend, so your Hugging Face token never reaches the browser.

> Status: this code has not been compiled or run by its author (only `script.js` was syntax-checked). Build and test it locally and fix anything your environment surfaces.

## Features
- **Summary:** exam-ready Markdown summary, rendered safely (no `innerHTML`), with a copy button.
- **Quiz:** exactly 5 MCQs, scored after submission, with explanations and correct/incorrect highlighting.
- **Flashcards:** exactly 8 cards with Show Answer, Know It / Review Again, live progress and Start Again.
- Loading states, clear errors, responsive and keyboard-accessible UI.

## Technology stack
- Frontend: HTML5, CSS3, vanilla JavaScript, Fetch API (no frameworks or build tools)
- Backend: Java 26, Spring Boot 4.0.x, Spring Web MVC, Maven, `RestTemplate`
- AI: Hugging Face Inference Router (OpenAI-compatible chat completions), `deepseek-ai/DeepSeek-V4.1-Flash`

## Architecture and request flow
1. The browser POSTs `{"notes": "..."}` to `http://localhost:8080/api/{notes|quiz|flashcards}`.
2. `NoteController` rejects blank or oversized notes (400).
3. `AiService` calls `https://router.huggingface.co/v1/chat/completions` with `Authorization: Bearer $HF_TOKEN`.
4. The service extracts `choices[0].message.content`. For quiz and flashcards it parses and validates the JSON (counts, fields, answer letters) and returns normalized JSON.
5. Any AI failure becomes HTTP 502 with `{"error": "..."}`.

## Project structure
```text
NotePilot-AI/
├── index.html
├── style.css
├── script.js
├── .gitignore
├── .env.example
├── README.md
└── backend/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/notepilot/notepilot/
        │   │   ├── NotepilotApplication.java
        │   │   ├── controller/NoteController.java
        │   │   ├── model/NoteRequest.java
        │   │   └── service/AiService.java
        │   └── resources/application.properties
        └── test/java/com/notepilot/notepilot/
            ├── controller/NoteControllerTest.java
            └── service/AiServiceTest.java
```

## Prerequisites
- JDK 26 (Spring Boot 4.0.x lists Java 17–26 support)
- Maven 3.9+ (or IntelliJ's bundled Maven)
- VS Code with the Live Server extension
- A Hugging Face account and access token that can call Inference Providers

## Hugging Face token setup
1. Create a token at https://huggingface.co/settings/tokens with permission to make Inference Provider calls.
2. Never commit it. `.env` files are git-ignored.

### IntelliJ IDEA
1. Open **Run → Edit Configurations…**
2. Select the Spring Boot configuration for `NotepilotApplication`.
3. Under **Environment variables**, add `HF_TOKEN=hf_xxx`.
4. Click **Apply/OK**, then stop and restart the application.

### Terminal alternatives
```bash
# macOS / Linux
export HF_TOKEN=hf_xxx && cd backend && mvn spring-boot:run
# Windows PowerShell
$env:HF_TOKEN="hf_xxx"; cd backend; mvn spring-boot:run
```
`.env.example` is documentation only. Spring Boot does not load `.env` files automatically.

## Run the frontend
Open the project folder in VS Code, right-click `index.html`, and choose **Open with Live Server**. The page must be at **`http://127.0.0.1:5500`**. The backend only allows that origin, and `localhost` and `127.0.0.1` are different origins.

## API
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/hello` | Health check |
| POST | `/api/notes` | Summary |
| POST | `/api/quiz` | 5-question quiz |
| POST | `/api/flashcards` | 8 flashcards |

```bash
curl -X POST http://localhost:8080/api/notes \
  -H "Content-Type: application/json" \
  -d '{"notes":"Photosynthesis converts light energy into chemical energy in chloroplasts."}'
```

Summary response: `{"summary": "# Photosynthesis\n..."}`

Quiz response:
```json
{"questions":[{"question":"...","options":{"A":"...","B":"...","C":"...","D":"..."},"answer":"B","explanation":"..."}]}
```

Flashcards response:
```json
{"flashcards":[{"question":"...","answer":"..."}]}
```

Errors: `{"error": "message"}` with HTTP 400 (bad input), 502 (AI/API failure) or 500 (unexpected).

## Why `content` can be `null`
The configured model is a reasoning model. It can return `message.content: null` with the text in `message.reasoning_content` when it spends its whole token budget thinking (`finish_reason: "length"`). Other hosts of this model document that reasoning tokens count against `max_tokens`. The Hugging Face router's exact behavior was not verified.

What the project does about it:
- `max_tokens` defaults to **8000** (`notepilot.ai.max-tokens`) instead of 2000.
- An optional `notepilot.ai.reasoning-effort` (for example `low`) is sent only if set. If the router rejects it, clear it.
- `reasoning_content` is never used as an answer. A null `content` becomes a clear 502 error, and the log shows `finish_reason` and token usage.

## Troubleshooting
- **"Could not resolve placeholder 'HF_TOKEN'" / app won't start:** the environment variable is missing. Set it in the run configuration and restart.
- **Frontend says it cannot reach the backend:** start the backend and check `http://localhost:8080/api/hello`.
- **CORS error:** open the page from `http://127.0.0.1:5500`, not `localhost:5500`. To allow another origin, edit `@CrossOrigin` in `NoteController`.
- **HTTP 401/403 from Hugging Face:** token invalid or lacking Inference Providers permission.
- **HTTP 404:** model name unavailable on your account or provider. Change `notepilot.ai.model`.
- **HTTP 429:** rate limit or quota reached. Wait and retry.
- **"used all of its output tokens" / null content:** raise `notepilot.ai.max-tokens` or set `notepilot.ai.reasoning-effort`.
- **"malformed JSON" / "unexpected format":** the model broke the schema. Retry. Details are in the backend log.
- **Build error on Spring Boot version:** use the latest `4.0.x` parent. Boot 4 uses Jackson 3 (`tools.jackson`), so on Boot 3.x the Jackson imports in `AiService` would need to change.

## Security
- The token lives only in the backend environment. Never put it in JS/HTML/CSS, `application.properties`, or Git.
- Quiz answers are sent to the browser with the quiz data (kept out of the UI until submission), so a technical user could read them in DevTools. Fine for a study tool, not for graded exams.

## Future improvements
Streaming responses, saving notes and history, PDF/Doc upload, spaced-repetition scheduling, rate limiting, and Docker packaging.
