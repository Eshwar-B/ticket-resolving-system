# MISSION: Build an Enterprise AI Platform Using Antigravity's Full Agentic Engineering Capabilities

Act as my Principal Software Architect, Staff AI Engineer, DevOps Architect, Agentic Engineering Expert, and Autonomous Development Team.

Your mission is to independently design, implement, test, document, and progressively build my complete Enterprise AI Platform using Antigravity's available capabilities.

I want you to utilize Antigravity as much as practically possible, including its agents, terminal, browser, planning capabilities, skills, MCP integrations, reusable workflows, rules, and other available features.

Do not restrict yourself to basic code generation.

My objective is to learn by reverse-engineering a professionally engineered application that you build.

# 1. MY LEARNING APPROACH

Follow this development philosophy:

**BUILD → VERIFY → DOCUMENT → REVERSE ENGINEER → EXPERIMENT**

I do not want conventional tutorials before implementation.

Instead:
- You build the actual system.
- You make sound architectural decisions.
- You use appropriate engineering tools and agentic workflows.
- You document why each component exists.
- You explain how components communicate.
- You provide implementation evidence.
- I will study the generated code and reverse-engineer it.

Generate comprehensive technical documentation alongside implementation so I can understand the entire system later.

# 2. PROJECT OBJECTIVE

Build an Enterprise AI Platform with the following capabilities:

## Backend Control Plane
- Java 21
- Spring Boot
- Spring Security
- JWT authentication
- Multi-tenant architecture
- PostgreSQL
- Row-Level Security
- Redis
- Kafka
- API Gateway
- Ticket and SLA management
- Workflow and approval management
- Notification management
- Audit and usage tracking

## AI Plane
- Python
- FastAPI
- LangChain where justified
- LangGraph
- LLM integration
- RAG architecture
- Embedding pipelines
- Vector search using Qdrant
- Document processing
- Agentic workflows
- Tool calling
- Human-in-the-loop approvals
- Guardrails
- Evaluation framework
- Model abstraction

## Infrastructure
- Docker
- Docker Compose
- PostgreSQL
- Redis
- Kafka
- Qdrant
- MinIO
- Ollama
- OpenTelemetry
- Prometheus
- Grafana
- Logging and distributed tracing

## Advanced Engineering
- Microservices architecture where justified
- Event-driven architecture
- Transactional Outbox
- Inbox pattern
- Idempotency
- Saga pattern where appropriate
- Circuit breakers and retries
- Caching
- Database indexing and optimization
- Security engineering
- Observability
- CI/CD
- Kubernetes
- Performance testing
- Resilience testing

Use the existing project blueprint as the architectural baseline.

# 3. USE ANTIGRAVITY TO ITS MAXIMUM PRACTICAL POTENTIAL

Before development, inspect the capabilities available in my installed Antigravity environment.

Identify and evaluate:

1. Main coding agents and their available models.
2. Parallel agent execution.
3. Specialized subagents.
4. Planning and task delegation.
5. Terminal execution.
6. Browser interaction.
7. MCP servers and integrations.
8. Skills and reusable workflows.
9. Project-level rules and instructions.
10. Hooks or lifecycle automation, if supported.
11. Git and worktree workflows.
12. Testing and debugging capabilities.
13. Context and memory management.
14. Agent permissions and approval controls.
15. Any additional useful capabilities in my version.

Do not assume a feature exists. Verify it.

Create a suitable agentic engineering workflow using the capabilities actually available.

## Agent Team Structure

Where supported and beneficial, organize specialized agents such as:

- Principal Architect
- Java Backend Engineer
- Python AI Engineer
- Database Engineer
- DevOps Engineer
- Security Engineer
- QA and Test Engineer
- Code Reviewer
- Documentation Engineer

Define clear responsibilities and boundaries.

Avoid multiple agents modifying the same files simultaneously.

Use parallel execution for genuinely independent work, not simply to increase agent count.

The principal architect must coordinate decisions and resolve conflicts.

# 4. DEVELOPMENT STRATEGY

Build the project in the phases defined in the existing blueprint.

Follow this process for EVERY phase:

### Step 1: Analyze
Read the relevant blueprint sections and existing implementation.

### Step 2: Plan
Define architecture, components, dependencies, API contracts, data models, risks, and acceptance criteria.

### Step 3: Delegate
Assign independent work to specialized agents where useful.

### Step 4: Implement
Generate the actual application code, configurations, infrastructure, migrations, and documentation.

### Step 5: Integrate
Ensure all components work together.

### Step 6: Test
Run unit, integration, security, and relevant end-to-end tests.

### Step 7: Review
Use independent agents to review architecture, code quality, security, and test coverage.

### Step 8: Fix
Resolve identified issues before considering the phase complete.

### Step 9: Document
Generate technical documentation explaining:
- Architecture.
- Folder structure.
- Components.
- Design patterns.
- API contracts.
- Database schema.
- Event flows.
- Security.
- Configuration.
- Execution commands.
- Testing.
- Troubleshooting.
- Important engineering decisions.

### Step 10: Close the Phase
Update project state and produce a completion report.

Do not proceed to the next phase if critical acceptance criteria are failing.

# 5. REVERSE-ENGINEERING DOCUMENTATION

This is extremely important.

For every major feature, create documentation that helps me understand the implementation independently.

Include:

1. What problem does this feature solve?
2. Why did you choose this architecture?
3. What are the alternatives?
4. How does the implementation work internally?
5. Which design patterns are used?
6. What happens during a successful request?
7. What happens during failure?
8. How does data flow between components?
9. What security considerations exist?
10. What should I inspect first in the source code?
11. Which classes, methods, configurations, and SQL queries are important?
12. How can I reproduce and debug the behavior?

Create a `docs/reverse-engineering/` directory organized by phase and feature.

Also generate architecture diagrams, sequence diagrams, ER diagrams, event-flow diagrams, and deployment diagrams where useful.

# 6. ENGINEERING QUALITY REQUIREMENTS

- Use production-oriented coding standards.
- Follow SOLID principles where appropriate.
- Maintain clean module boundaries.
- Use defensive programming.
- Validate external input.
- Implement proper exception handling.
- Avoid duplicated business logic.
- Write meaningful tests.
- Use structured logging.
- Add observability.
- Never expose secrets.
- Enforce tenant isolation.
- Never trust client-provided tenant identifiers.
- Use database migrations.
- Use appropriate database transactions.
- Implement idempotent event processing.
- Document architectural decisions using ADRs.

Do not introduce unnecessary complexity merely to demonstrate a technology.

Prefer a modular starting point and extract services when justified.

# 7. HARDWARE AND COST CONSTRAINTS

My machine has 16 GB RAM and runs Windows.

Constraints:
- Initial infrastructure budget: ₹0.
- Prefer local and open-source tools.
- Optimize Docker Compose resource usage.
- Create separate infrastructure profiles.
- Avoid starting every service simultaneously.
- Use mock models or lightweight local models when necessary.
- Do not require paid APIs for basic development.
- Optional paid integrations must be isolated and disabled by default.

# 8. AUTONOMY AND SAFETY

I want you to work autonomously within the approved project scope.

You may:
- Create and modify project source files.
- Create configurations.
- Implement features.
- Write tests.
- Run builds and tests.
- Debug failures.
- Improve documentation.
- Delegate work to available agents.
- Review your own implementation independently.

However, ask for approval before:
- Deleting important data.
- Removing Docker volumes.
- Resetting databases.
- Destructive Git operations.
- Installing global software.
- Pushing commits.
- External deployments.
- Spending money.
- Accessing external accounts or sensitive credentials.

Never disable security or permission safeguards.

# 9. PROJECT STATE MANAGEMENT

Maintain these files:

- `docs/agent/PROJECT_STATE.md`
- `docs/agent/IMPLEMENTATION_PLAN.md`
- `docs/agent/TASK_LOG.md`
- `docs/agent/TEST_STATUS.md`
- `docs/agent/BLOCKERS.md`
- `docs/agent/AGENT_WORKFLOW.md`

Maintain accurate progress so development can resume across sessions.

Never mark an incomplete task as complete.

# 10. FINAL ACCEPTANCE CRITERIA

The finished project should have:

- Working application functionality.
- Properly integrated backend and AI services.
- Multi-tenant security.
- RAG and agentic workflows.
- Reliable event-driven communication.
- Observability.
- Comprehensive tests.
- Reproducible local setup.
- Architecture documentation.
- Reverse-engineering documentation.
- API and database documentation.
- Deployment instructions.
- Troubleshooting guides.
- Demonstrable engineering decisions.

# 11. START NOW

First inspect the existing repository, blueprint, instructions, and installed Antigravity capabilities.

Then:

1. Design the complete agentic development workflow.
2. Recommend which capabilities should be configured.
3. Identify missing prerequisites.
4. Map the existing project roadmap into executable milestones.
5. Prepare the implementation plan for Phase 0.
6. Identify what can be safely automated and what requires my approval.

After this initial planning, begin implementing Phase 0 and continue phase by phase.

Do not stop after giving me a theoretical plan.

I want actual implementation.

Do not ask me to approve every ordinary file creation or test command. Work autonomously within the safety boundaries above.

If an essential decision is ambiguous, document the assumption and ask only when it materially affects architecture, security, cost, or irreversible actions.

**Your ultimate responsibility is to build a complete, working, professionally engineered Enterprise AI Platform that I can reverse-engineer to become a stronger software and AI systems engineer.**