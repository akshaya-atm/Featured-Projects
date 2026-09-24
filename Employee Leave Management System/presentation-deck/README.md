# ELMS interview deck

Open `index.html` in a browser. This is a local, interactive HTML deck — no build step, no CDN, no live ELMS dependency.

## Presenting

- Arrow keys, Page Up / Page Down or the navigation buttons change slides.
- Home / End jump to the beginning / end.
- **Visuals are the default tab.** Slides 4–6 also have code excerpts for backend implementation details.
- N opens presenter notes with talking points, scope boundaries and source references. Keep notes closed during screen sharing unless prompted.
- Auto play starts paused and advances every 20 seconds when enabled.
- Fullscreen works where the browser allows it.
- Browser printing renders all seven slides in landscape.

## Interview narrative

Technical walkthrough, ~8-10 minutes:

1. **Product** — ELMS: a leave workflow with policy RAG and authenticated tools shared by in-app chat and MCP clients.
2. **The Problem** — How do you let an LLM mutate business data without trusting it? (Identity, Grounding, Authorization, Concurrency)
3. **Architecture** — Browser REST and external MCP calls reach the same Spring Boot leave services.
4. **Agent & tool design** — One @Tool implementation, two client surfaces (in-app + MCP), server-owned identity.
5. **Transactional correctness** — The invariant, the reserve/settle lifecycle, and database-enforced locks.
6. **Grounding** — The policy search tool returns retrieved passages for the assistant's answer.
7. **Looking forward** — Built features, untested areas and concrete next work.

## Key talking points per slide

| Slide | Lead with | Avoid |
|-------|-----------|-------|
| 1 | "ELMS uses policy retrieval and authenticated tools to help employees manage leave" | Explaining architecture too early |
| 2 | A prompt must not determine employee identity or approval authority | Listing technologies without explaining the problem |
| 3 | Browser REST and MCP tool calls reach the same leave services | Walking through every single arrow |
| 4 | "Employee identity never comes from model arguments" | Assuming MCP inherently hides tools |
| 5 | "available = entitled + carry-forward − used − reserved" | Making locking sound like an afterthought |
| 6 | Use the "annual leave carry-forward" example | Leading with threshold metrics |
| 7 | Name the missing concurrency and RAG evaluations | Claiming production readiness |

## Claim boundaries

Content comes from the current ELMS source. Relevant source filenames appear in presenter notes where applicable. This deck does not claim production deployment, usage numbers, performance benchmarks, measured time savings, zero hallucinations, or comprehensive test coverage.

Known distinctions retained in the narrative:

- Native chat conditionally adds manager tools. MCP registers all tool groups and relies on backend authorization.
- Explicit submission intent is a model instruction, not a separate server-enforced confirmation token.
- Dashboard refresh follows an in-app response. External MCP changes do not push into the browser.
- The policy specifies a certificate due date, but reminder selection currently checks unshown/missing certificates without calculating that deadline.
- Certificate MIME/size checks do not establish file-signature validation or malware scanning.
- No half-day support, HR approval stage, staffing-coverage engine or automatic entitlement-accrual process is claimed.
- Automated tests currently exist. Database concurrency and RAG answer quality have not been formally evaluated.
