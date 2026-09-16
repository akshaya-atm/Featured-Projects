# 🏝️ AI Outlast — Autonomous LLM Multi-Agent Survivor Simulator

**AI Outlast** is a fully autonomous, AI-driven simulation of a *Survivor*-style social strategy game. A cast of LLM-controlled contestants—each configured with a distinct personality archetype, core stats, and hidden agendas—compete on a virtual island. They draft teams, negotiate, form/break alliances, send secret whispers, vote opponents off, merge, and compete for a Sole Survivor title judged by an eliminated player jury.

A **Spring Boot 4 (Java 17)** backend orchestrates the simulation turn by turn, while a lightweight **2D web control room frontend** lets you watch the live strategic drama unfold in real time.

---

## 🎮 How the Simulation Works

1. **Draft Phase**: Captains are selected and teams are drafted via an LLM-driven pick process based on contestant stats and personalities.
2. **Camp Life & Secret Whispers**: Teammates introduce themselves and exchange private 1-to-1 messages—building trust, proposing alliances, or executing deceptive whispers.
3. **Immunity Challenges**: The Host invents themed challenge scenarios. Each team pitches plans, votes on a team strategy, and the Host judges the winning submission.
4. **Tribal Council**: The losing team strategizes in secret and casts votes. Occasional Host twists (double eliminations, tie-break fire-making challenges) keep the game dynamic.
5. **The Merge**: Once the cast shrinks to ~70% size, remaining players merge into a single tribe for individual immunity challenges and free-for-all strategy.
6. **Endgame Jury Threat Cuts**: As active players drop to $\le 5$, agents with high `Social Intelligence` calculate jury threat win probabilities and target high-threat confirmed allies before the finale.
7. **Jury Finale**: The final two contestants face a jury of eliminated players who evaluate candidates based on game summaries and recorded social memories.

---

## 🧠 What Makes Each Contestant Unique

- **Personality Archetypes**: *Calm Strategist*, *Chaotic Provocateur*, *Diplomat*, *Bold Risk-Taker*, *Honest Philosopher*, etc.—shaping how agents reason and interact.
- **Six Core Stats**: `Trustworthiness`, `Emotional Stability`, `Cooperation`, `Social Intelligence`, `Survival Grit`, and `Assertiveness`.
- **Asymmetric Social Memory (`PlayerRelation`)**: Every player independently tracks trust scores (0–100), private notes, and asymmetric alliance statuses (`NONE` → `PROPOSED` → `CONFIRMED` → `BROKEN`).
- **Probabilistic Deception Rolls**: Whispers support honest vs. deceptive messaging, with lie detection probabilities calculated from agent stats (`100 - Trustworthiness` + `Assertiveness`).
- **Host Persona**: The Host operates as an independent LLM persona ("Dramatic Showman") who creates challenges, judges submissions, announces twists, and narrates eliminations.

---

## 🏗️ Architecture

```
                                +---------------------------+
                                |     GameController        |  <--- REST API
                                +-------------+-------------+
                                              |
                                              v
                                +---------------------------+
                                |   GameSimulationRunner    |  <--- Background Worker Thread
                                +-------------+-------------+
                                              |
       +--------------------+-----------------+--------------------+--------------------+
       |                    |                 |                    |                    |
       v                    v                 v                    v                    v
+--------------+   +-----------------+  +------------+   +-------------------+  +--------------+
| GameInit     |   | SetUpTeams      |  | HostAgent  |   | CampLifePhase     |  | TribalCouncil|
| (Player init)|   | (LLM Draft Pick)|  | (Host LLM) |   | (Whispers & Memory|  | (Strategy &  |
+--------------+   +-----------------+  +------------+   |  Reflections)     |  |  Voting)     |
                                                         +-------------------+  +--------------+
```

```
AI outlast/
├── Outlast-Backend/         Spring Boot backend (Java 17)
│   ├── src/main/java/com/akshaya/outlast/
│   │   ├── agent/           Phase logic: draft, camp life, challenges, tribal council
│   │   ├── client/          Multi-provider LLM client (OpenAI Java SDK)
│   │   ├── config/          CORS & game rules configuration
│   │   ├── context/         In-memory thread-safe game state (GameContext)
│   │   ├── controller/      REST API endpoints (GameController)
│   │   ├── model/           Player, Team, Challenge, Vote, and relation records
│   │   ├── prompt/          System prompt builders for players and the Host
│   │   └── utils/           Personas, log capture, Jackson JSON helpers
│   └── src/main/resources/application.yaml   LLM provider configs & game limits
├── Outlast-Frontend/        Static 2D control room web UI (HTML/CSS/Vanilla JS)
│   ├── index.html
│   ├── app.js               Polls backend REST API and renders live simulation feed
│   └── style.css
└── presentation-deck/       Interactive Technical Presentation Deck (HTML/CSS/JS)
    ├── index.html
    ├── deck.css
    └── deck.js
```

---

## 🛠️ Tech Stack & Multi-Provider LLM Integration

- **Backend**: Java 17, Spring Boot 4.1.0 (Spring Web MVC), Maven
- **Multi-Provider LLM Routing**: `openai-java` official SDK (`com.openai:openai-java`) configured for OpenAI-compatible endpoint routing:
  - **Groq** (`https://api.groq.com/openai/v1`)
  - **Google Gemini** (`https://generativelanguage.googleapis.com/v1beta/openai`)
  - **Cerebras** (`https://api.cerebras.ai/v1`)
  - **Ollama** (Local LLM server, `http://localhost:11434/v1`)
- **Fault Tolerance**: 1.5s rate-limit pacing delay, exponential backoff retries (4s → 64s), robust Jackson JSON parsing, and deterministic stat-driven fallback logic when LLM APIs time out.
- **Frontend**: Lightweight HTML5, CSS3, and Vanilla JS control dashboard (zero build step).

---

## 📡 REST API Summary

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/game/start` | Starts a new simulation run with custom or default contestants |
| `GET` | `/api/game/state` | Returns current round, phase, team rosters, and active players |
| `GET` | `/api/game/logs?from=N` | Fetches game log lines emitted since log index `N` |
| `POST` | `/api/game/next` | Resumes simulation when running in step-by-step mode |
| `POST` | `/api/game/reset` | Resets active simulation state |

---

## 🚀 Running the Project

### Prerequisites
- **Java 17+** and **Maven** (or use `./mvnw`)

### 1. Configure LLM API Key
In `Outlast-Backend/src/main/resources/application.yaml`, set `llm.provider` to your preferred provider (`groq`, `gemini`, `ollama`, or `cerebras`), then export the matching API key:

```bash
export GROQ_API_KEY="your_groq_api_key"
# or export GEMINI_API_KEY="your_gemini_api_key"
```

### 2. Start the Backend
From the `Outlast-Backend/` directory:
```bash
cd Outlast-Backend
./mvnw spring-boot:run
```
The REST API will be available at **http://localhost:8081**.

### 3. Launch the Control Room Dashboard
Open `Outlast-Frontend/index.html` in any web browser. Configure team sizes and contestant names, then click **Set foot on the island** to watch the simulation play out live!

---

## 📝 License
This project is open source and available under the [MIT License](LICENSE).
