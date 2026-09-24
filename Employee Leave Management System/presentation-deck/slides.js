/* ELMS Technical Interview Deck — 7 slides, ~8-10 minutes.
   Narrative: Product → Problem → Architecture → Agent/tool design →
   Transactional correctness → RAG → Tradeoffs
   Visuals/diagrams are the primary evidence, code supports key engineering points. */
window.ELMS_SLIDES = [

  /* ──────────────────────────── 1. HERO / PRODUCT ──────────────────────────── */
  {
    section: "Product",
    title: "Employee Leave Management",
    accent: "System with RAG + MCP",
    lead: "Employees can ask policy questions or submit leave through chat. The assistant searches company policy with RAG and uses authenticated tools that also serve MCP clients.",
    tags: ["Policy RAG", "Shared MCP tools", "Transactional leave"],
    points: [
      ["In-app assistant", "It calls search_company_leave_policy for HR questions and apply_leave when an employee asks to submit leave."],
      ["MCP interoperability", "The same authenticated tools are exposed to external MCP clients through the Model Context Protocol."],
      ["Leave workflow", "Spring Boot services reserve balance and route requests through the required approvers."]
    ],
    screenshot: "01-employee-dashboard.png", screenshotTitle: "Employee dashboard",
    screenshotHint: "The full employee dashboard showing balances, request list, and the Leave Assistant chat panel.",
    footer: "Role: Full-stack Developer · Spring Boot 4.1 · Vanilla JS",
    notes: "Start with the product: Employee Leave Management System. Employees use a browser and in-app assistant to check policy, inspect balances and submit leave. Policy questions use a retrieval tool backed by pgvector; the authenticated tool layer is also exposed over MCP. Domain services, not the model, enforce leave rules.",
    sources: ["ELMS-Frontend/index.html"]
  },

  /* ──────────────────────────── 2. THE PROBLEM ──────────────────────────── */
  {
    section: "The Problem",
    title: "The problem with",
    accent: "LLM-driven leave requests",
    lead: "How can an LLM submit leave without trusting its claims about identity, policy, approval authority or available balance?",
    tags: ["Identity", "Grounding", "Authorization", "Concurrency"],
    points: [
      ["Identity spoofing", "If the LLM passes the employee ID as a tool argument, a prompted user could easily act on behalf of others."],
      ["Policy & dates", "A model can invent policy details or misread relative dates such as 'next Monday'."],
      ["Authorization bypass", "AI tools shouldn't bypass managerial approval hierarchies or segregation of duties."],
      ["Concurrency races", "Simultaneous requests could reserve the same balance unless the backend serializes them."]
    ],
    screenshot: "02-problem-constraints.svg", screenshotTitle: "The Four Constraints",
    screenshotHint: "Visual showing the LLM surrounded by 4 barriers: Identity, Grounding, Authorization, Concurrency.",
    notes: "Explain four risks before showing the implementation: employee ID spoofing, inaccurate policy or date interpretation, approval bypass and concurrent balance reservations.",
    sources: []
  },

  /* ──────────────────────────── 3. ARCHITECTURE ──────────────────────────── */
  {
    section: "Architecture",
    title: "ELMS architecture",
    accent: "from clients to PostgreSQL",
    lead: "The browser uses REST; external agents use MCP. Both reach Spring Boot services that check identity, leave rules and approvals.",
    tags: ["Browser", "MCP Client", "Tool Boundary", "Domain Services"],
    points: [
      ["Client entry points", "REST controllers serve the browser; the MCP server exposes authenticated @Tool methods."],
      ["Models", "Gemini 3.5 Flash-Lite handles chat, with Ollama embeddings for policy retrieval."],
      ["Tool calls", "The server supplies authenticated employee context and calls the same leave services used by REST endpoints."]
    ],
    screenshot: "architecture.svg", screenshotTitle: "Simplified Architecture",
    screenshotHint: "Browser and MCP clients reach Spring Boot services backed by PostgreSQL.",
    footer: "Spring Boot 4.1 · Java 25 · PostgreSQL 17",
    notes: "Walk through the two entry points. Browser requests use REST; external agents use MCP. The assistant and MCP server share tools, and those tools call the same leave services used by REST controllers. Point out where JWT identity enters and where PostgreSQL stores leave balances and requests.",
    sources: []
  },

  /* ──────────────────────────── 4. SIGNATURE TECH SLIDE ──────────────────────────── */
  {
    section: "Agent & Tool Design",
    title: "One tool layer,",
    accent: "two agent surfaces",
    lead: "The same @Tool implementation serves the in-app ChatClient and external MCP clients. Identity is injected from the server context, never from model arguments.",
    tags: ["@Tool", "ToolCallContext", "MCP", "Streamable HTTP"],
    points: [
      ["Server-owned identity", "Employee identity is extracted from the JWT Principal, injected into the native ToolContext or MCP transport context, and never trusted from the LLM prompt."],
      ["Dynamic tools & grounding", "Manager tools are bound conditionally. System prompts inject absolute company dates, and tool descriptions explicitly forbid relative date formats."],
      ["Tool errors", "The assistant unwraps Spring AI ToolExecutionExceptions and returns domain-specific AppExceptions to the client."]
    ],
    screenshot: "04-mcp-tools.svg", screenshotTitle: "Unified Tool Execution",
    screenshotHint: "One @Tool in the centre, ChatClient entering from one side, MCP clients from the other, JWT supplying identity underneath.",
    footer: "Spring AI ChatClient · MCP Streamable HTTP transport",
    codeLabel: "Unified identity & execution",
    codeFile: "EmployeeTools.java / ToolCallContext.java",
    code: `// Abridged from EmployeeTools.java and ToolCallContext.java
public LeaveApplicationService.ApplicationResult applyLeave(
        String leaveType, LocalDate startDate, LocalDate endDate,
        String reason, ToolContext toolContext) {
    LeaveApplicationService.ApplicationResult result = leaveApplicationService.apply(
            ToolCallContext.requireEmployeeId(toolContext),
            leaveType, startDate, endDate, reason);
    ToolCallContext.markDashboardChanged(toolContext);
    // Certificate-upload handling omitted here.
    return result;
}

public static String requireEmployeeId(ToolContext toolContext) {
    Object nativeEmployeeId = toolContext == null
            ? null : toolContext.getContext().get(EMPLOYEE_ID_KEY);
    if (nativeEmployeeId instanceof String employeeId && !employeeId.isBlank()) {
        return employeeId;
    }
    Object mcpEmployeeId = McpToolUtils.getMcpExchange(toolContext)
            .map(exchange -> exchange.transportContext())
            .map(context -> context.get(EMPLOYEE_ID_KEY))
            .orElse(null);
    if (mcpEmployeeId instanceof String employeeId && !employeeId.isBlank()) {
        return employeeId;
    }
    throw new AppException(AuthErrorCode.TOKEN_MISSING,
            "Authenticated employee context is unavailable");
}`,
    notes: "Show the shared @Tool implementation. In-app ChatClient and external MCP clients use it, while server context supplies the employee ID. The method does not accept an employee ID from model arguments.",
    sources: ["ELMS-Backend/src/main/java/com/akshaya/elmsbackend/tool/EmployeeTools.java", "ELMS-Backend/src/main/java/com/akshaya/elmsbackend/tool/ToolCallContext.java"]
  },

  /* ──────────────────────────── 5. CORRECTNESS ──────────────────────────── */
  {
    section: "Transactional Correctness",
    title: "Balance reservation",
    accent: "and staged approval",
    lead: "Pending applications reserve balance immediately via pessimistic locks. Stage-gated routing decides who can settle the transaction.",
    tags: ["PESSIMISTIC_WRITE", "Two-phase balance", "CHECK constraints", "Stage-gated routing"],
    points: [
      ["The invariant", "available = entitled + carry-forward − used − reserved. Enforced by database CHECK constraints."],
      ["Two-phase lifecycle", "Apply → Reserve → Pending approvals → Settle / Release. Locks and constraints protect against overlapping reservations."],
      ["Stage-gated routing", "Business rules define sequential approvers. The service layer verifies the actor matches the current stage. Self-approval is explicitly rejected."]
    ],
    screenshot: "05-lifecycle.svg", screenshotTitle: "Reservation Lifecycle",
    screenshotHint: "Visual showing: Apply -> Reserve -> Pending Approvals -> Settle/Release.",
    footer: "Pessimistic employee lock · balance CHECK constraint",
    codeLabel: "Database invariant & locking",
    codeFile: "V2__create_leave_schema.sql / LeaveDecisionService.java",
    code: `-- Database-enforced balance invariant
CONSTRAINT chk_leave_balance_available
    CHECK (
        used_days + reserved_days
            <= entitled_days + carried_forward_days
    )

// Serialize concurrent leave applications per employee
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select e from Employee e where e.employeeId = :employeeId")
Optional<Employee> findForLeaveApplication(@Param("employeeId") String employeeId);

// Service-level authorization
if (owner.getId().equals(actor.getId())) {
    throw error(LeaveErrorCode.NOT_YOUR_TEAM, "An employee cannot approve their own leave request");
}`,
    notes: "Explain the balance calculation: available = entitled + carry-forward - used - reserved. A request reserves days before approval. Approval settles the reservation; rejection releases it. The employee lock and database CHECK constraint protect the balance, while the service checks the current approver.",
    sources: ["ELMS-Backend/src/main/resources/db/migration/V2__create_leave_schema.sql", "ELMS-Backend/src/main/java/com/akshaya/elmsbackend/employee/repository/EmployeeRepository.java", "ELMS-Backend/src/main/java/com/akshaya/elmsbackend/leave/LeaveDecisionService.java"]
  },

  /* ──────────────────────────── 6. GROUNDING ──────────────────────────── */
  {
    section: "Grounding",
    title: "Policy answers use",
    accent: "retrieved passages",
    lead: "For policy questions, the assistant calls a search tool that retrieves relevant passages from the company leave policy. The model then uses those passages to compose an answer.",
    tags: ["RAG", "pgvector", "Ollama", "Policy search tool"],
    points: [
      ["The query pipeline", "Question → nomic-embed-text → pgvector search → search_company_leave_policy results → Gemini answer."],
      ["Retrieval settings", "The search returns up to four passages above a 0.65 similarity threshold. Answer quality still needs evaluation."],
      ["Idempotent indexing", "Startup indexing hashes content to prevent duplicate embeddings on restart."]
    ],
    screenshot: "06-rag-example.svg", screenshotTitle: "Grounded Answer Example",
    screenshotHint: "Show: 'How much annual leave can I carry forward?' -> retrieved policy passages -> grounded answer.",
    footer: "CompanyPolicyTools calls CompanyPolicyRagService",
    codeLabel: "Similarity search",
    codeFile: "CompanyPolicyRagService.java",
    code: `SearchRequest request = SearchRequest.builder()
        .query(question.trim())
        .topK(4)
        .similarityThreshold(0.65)
        .build();

return vectorStore.similaritySearch(request);`,
    notes: "Use the annual-leave carry-forward example. The model is instructed to call search_company_leave_policy. CompanyPolicyTools delegates to CompanyPolicyRagService, which searches pgvector with topK=4 and a 0.65 threshold. Retrieved passages return as tool output; the model composes the answer. This improves grounding but does not guarantee factual answers, and retrieval precision has not been formally evaluated.",
    sources: ["ELMS-Backend/src/main/java/com/akshaya/elmsbackend/tool/CompanyPolicyTools.java", "ELMS-Backend/src/main/java/com/akshaya/elmsbackend/rag/CompanyPolicyRagService.java", "ELMS-Backend/src/main/java/com/akshaya/elmsbackend/agent/AiAssistantService.java"]
  },

  /* ──────────────────────────── 7. SCOPE & NEXT ──────────────────────────── */
  {
    section: "Looking Forward",
    title: "What works today",
    accent: "and what remains",
    lead: "The leave workflow and AI tools are built. Database concurrency and RAG answer quality still need dedicated evaluation.",
    tags: ["Built", "Boundaries", "Next"],
    points: [
      ["What's built", "Leave rules with cross-year splitting, multi-stage approval, authenticated AI tools (native + MCP), policy RAG, and transactional reservations."],
      ["Current boundaries", "Automated tests exist, but database concurrency and RAG answer quality have not been formally evaluated. No production deployment is claimed."],
      ["Next priorities", "1. Database concurrency tests. 2. Formal RAG precision evaluation. 3. Server-enforced confirmation tokens for AI mutations."]
    ],
    screenshot: "07-roadmap.svg", screenshotTitle: "Project Roadmap",
    screenshotHint: "Roadmap diagram outlining what is built, known boundaries, and next sprint priorities.",
    footer: "ELMS · Spring Boot · PostgreSQL · policy RAG · MCP",
    extraTab: "Scope details",
    extra: [
      ["Built", "Leave rules, cross-year balance reservations, ordered approval stages, authenticated AI tool calling (native + MCP), company policy RAG."],
      ["Boundaries", "Automated tests exist, but not a formal concurrency or RAG evaluation. No production deployment, HR approval stage or automatic accrual is claimed."],
      ["Next", "1. Database concurrency tests. 2. RAG precision evaluation. 3. Server-enforced AI mutation confirmation tokens."]
    ],
    notes: "Close with built features, current boundaries and next priorities. Automated tests are present, but real database concurrency and RAG answer quality still need dedicated evaluation. Do not claim production deployment. The 0.65 threshold is configured but not formally tuned. Server-enforced confirmation for AI mutations remains future work.",
    sources: []
  }
];
