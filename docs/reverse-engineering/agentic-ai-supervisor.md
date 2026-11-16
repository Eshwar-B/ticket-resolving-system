# Reverse Engineering: LangGraph Multi-Agent Architecture & Human Approvals

## 1. Single LLM Prompt vs. Agentic Supervisor
In enterprise workflows, relying on a single prompt or monolithic LLM call for complex reasoning leads to:
- Uncontrolled hallucinations on financial commitments.
- Missing or malformed tool arguments.
- High token latency and failure to recover from tool errors.

The platform uses a **stateful multi-agent supervisor graph** built on LangGraph.

---

## 2. Agent Graph Topology

```mermaid
flowchart TD
    Ingest["Ticket Created Event"] --> Guardrail["Input Guardrail (Injection & PII Check)"]
    Guardrail --> Triage["Triage Agent (Categorization & Sentiment)"]
    Triage --> Supervisor{"Supervisor Router"}
    
    Supervisor -->|Needs Policy Info| RetrievalAgent["Retrieval Agent (Hybrid RAG)"]
    RetrievalAgent --> Supervisor
    
    Supervisor -->|Needs Verification| ToolAgent["Allow-Listed Tool Agent (Order/SLA Status)"]
    ToolAgent --> Supervisor
    
    Supervisor -->|Ready to Plan| ResolutionAgent["Resolution Agent (Draft Resolution & Plan)"]
    ResolutionAgent --> Critic["Critic Agent (Hallucination & Policy Verification)"]
    
    Critic -->|High Risk Action (e.g. Refund > $100)| HumanGate["Human Approval Gate (Workflow Service)"]
    Critic -->|Autonomous Resolution| OutputGuardrail["Output Guardrail"]
    
    HumanGate -->|Manager Approved| OutputGuardrail
    HumanGate -->|Manager Rejected| Supervisor
    
    OutputGuardrail --> FinalAction["Update Ticket & Notify Customer"]
```

---

## 3. Specialized Specialized Agent Roles

1. **Triage Agent (`triage_agent.py`):**
   - Classifies category (`RETURN_REFUND`, `BILLING_DISPUTE`, `TECHNICAL_ISSUE`, `ACCOUNT_ACCESS`).
   - Assesses urgency and sentiment.
2. **Retrieval Agent (`retrieval_agent.py`):**
   - Interfaces with Knowledge Service via HTTP/RRF.
   - Extracts relevant legal/policy clauses with verifiable citations.
3. **Resolution Agent (`resolution_agent.py`):**
   - Generates candidate response and proposes concrete actions (`AUTONOMOUS_RESOLVE`, `HUMAN_APPROVAL`, `ESCALATE`).
4. **Critic Agent (`critic_agent.py`):**
   - Independent verification pass.
   - Confirms that every claim in the resolution has a matching citation from the retrieval agent.
   - Flags policy violations or ungrounded promises before execution.
5. **Guardrails (`input_guardrail.py`, `output_guardrail.py`):**
   - Rejects prompt injections and masks PII on input.
   - Blocks unauthorized financial commitments on output.
