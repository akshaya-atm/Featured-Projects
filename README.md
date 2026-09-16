# ⭐ Featured Technical Projects

Welcome to the **Featured Projects Repository**. This repository contains deep-dive software engineering showcase projects highlighting low-level design, multithreaded concurrency, framework-free web architectures, and autonomous LLM multi-agent systems.

---

## 🚀 Projects Included

### 1. 🏝️ [AI Outlast](./AI%20outlast) — Autonomous LLM Multi-Agent Survivor Simulator
* **Stack**: Java 17, Spring Boot 4, Official OpenAI Java SDK, Groq (Llama 3.3), Google Gemini 2.5/3.6, Vanilla JS UI.
* **Highlights**: Decoupled background thread simulation runner, asymmetric social memory matrix (`PlayerRelation`), stat-driven probabilistic lie detection, endgame jury threat cut calculations ($\le 5$ active players), and interactive presentation deck.
* **Presentation Deck**: [`AI outlast/presentation-deck/index.html`](./AI%20outlast/presentation-deck/index.html)

### 2. 🛗 [Lift Management](./Lift%20Management) — Multithreaded Elevator Simulator
* **Stack**: Plain Java 11+, `java.util.concurrent`, `PriorityBlockingQueue`, Monitor Locks (`wait`/`notifyAll`).
* **Highlights**: 1 worker thread per elevator (`Lift implements Runnable`), SCAN elevator algorithm using dual min/max priority blocking queues (`upStops` vs `downStops`), zero busy-waiting boarding synchronization, and dynamic queue re-bucketing.
* **Presentation Deck**: [`Lift Management/presentation-deck/index.html`](./Lift%20Management/presentation-deck/index.html)

### 3. 🛒 [ShopSphere](./ShopSphere) — Framework-Free Full-Stack E-Commerce Engine
* **Stack**: Java Servlets (`HttpServlet`), PostgreSQL, HikariCP, ThreadLocal JWT Auth, Vanilla JS Control Room.
* **Highlights**: Built with 16 raw HttpServlet dispatchers without Spring Boot overhead, thread-safe `UserAuthFilter` mounting `UserContext` onto `ThreadLocal`, role-isolated guest vs admin LLM tools, and anti-zombie token SQL validation.
* **Presentation Deck**: [`ShopSphere/presentation-deck/index.html`](./ShopSphere/presentation-deck/index.html)

---

## 🛠️ Repository Structure

```text
Featured-Projects/
├── .gitignore
├── README.md
├── AI outlast/
│   ├── Outlast-Backend/
│   ├── Outlast-Frontend/
│   └── presentation-deck/
├── Lift Management/
│   ├── src/
│   └── presentation-deck/
└── ShopSphere/
    ├── ShopSphere-Backend/
    ├── ShopSphere-Frontend/
    └── presentation-deck/
```

---

## 📝 License
This monorepo is open source and available under the [MIT License](LICENSE).
