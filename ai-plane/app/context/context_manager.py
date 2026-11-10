"""
Enterprise Context Manager.
Assembles context in strict blueprint order:
1. System Policy
2. Tenant Config & Rules
3. Customer Profile
4. Retrieved Documents & Citations
5. Conversation History & Summary
6. Current Ticket / Task

Enforces strict token budgets and progressive context compression without losing citations.
"""
from typing import Dict, Any, List, Optional

DEFAULT_MAX_CONTEXT_TOKENS = 4096

class ContextManager:
    """Manages prompt context assembly and token budget compression."""

    def __init__(self, max_tokens: int = DEFAULT_MAX_CONTEXT_TOKENS):
        self.max_tokens = max_tokens

    @staticmethod
    def estimate_tokens(text: str) -> int:
        """Approximates token count (avg ~4 characters per token)."""
        return max(1, len(text) // 4)

    def assemble_context(
        self,
        system_policy: str,
        tenant_config: Dict[str, Any],
        customer_profile: Dict[str, Any],
        retrieved_chunks: List[Dict[str, Any]],
        conversation_history: List[Dict[str, str]],
        current_ticket: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Assembles prompt layers adhering strictly to blueprint order."""
        # Layer 1: System policy
        layer_system = f"[SYSTEM POLICY]\n{system_policy.strip()}"

        # Layer 2: Tenant config
        layer_tenant = (
            f"[TENANT CONFIG]\nTenant ID: {tenant_config.get('tenant_id', 'unknown')}\n"
            f"Auto-Refund Threshold: ${tenant_config.get('auto_refund_limit', 0.0)}\n"
            f"SLA Tier: {tenant_config.get('sla_tier', 'STANDARD')}"
        )

        # Layer 3: Customer profile
        layer_customer = (
            f"[CUSTOMER PROFILE]\nCustomer ID: {customer_profile.get('customer_id', 'anonymous')}\n"
            f"Tier: {customer_profile.get('tier', 'STANDARD')}\n"
            f"Total Orders: {customer_profile.get('order_count', 0)}"
        )

        # Layer 4: Retrieved knowledge chunks (Priority preservation)
        chunk_lines = []
        for i, chunk in enumerate(retrieved_chunks):
            doc_title = chunk.get("document_title", "Document")
            doc_ver = chunk.get("version", 1)
            chunk_id = chunk.get("chunk_index", i)
            content = chunk.get("content", "").strip()
            chunk_lines.append(f"[Doc: \"{doc_title}\" v{doc_ver} #chunk-{chunk_id}]\n{content}")
        layer_chunks = "[RETRIEVED KNOWLEDGE]\n" + ("\n\n".join(chunk_lines) if chunk_lines else "None")

        # Layer 6: Current ticket
        layer_task = (
            f"[CURRENT TASK]\nTicket ID: {current_ticket.get('id', 'unknown')}\n"
            f"Subject: {current_ticket.get('subject', '')}\n"
            f"Description: {current_ticket.get('description', '')}\n"
            f"Priority: {current_ticket.get('priority', 'MEDIUM')}"
        )

        # Layer 5: Conversation history (Subject to progressive compression)
        # Calculate budget remaining for history
        fixed_text = f"{layer_system}\n\n{layer_tenant}\n\n{layer_customer}\n\n{layer_chunks}\n\n{layer_task}"
        fixed_tokens = self.estimate_tokens(fixed_text)
        budget_for_history = max(100, self.max_tokens - fixed_tokens)

        # Truncate oldest messages if history exceeds budget
        included_history: List[str] = []
        history_tokens = 0
        for msg in reversed(conversation_history):
            line = f"{msg.get('role', 'user')}: {msg.get('content', '')}"
            line_tokens = self.estimate_tokens(line)
            if history_tokens + line_tokens <= budget_for_history:
                included_history.insert(0, line)
                history_tokens += line_tokens
            else:
                break

        layer_history = "[CONVERSATION HISTORY]\n" + ("\n".join(included_history) if included_history else "No prior history")

        # Full prompt
        full_prompt = (
            f"{layer_system}\n\n"
            f"{layer_tenant}\n\n"
            f"{layer_customer}\n\n"
            f"{layer_chunks}\n\n"
            f"{layer_history}\n\n"
            f"{layer_task}"
        )

        total_tokens = self.estimate_tokens(full_prompt)

        return {
            "assembled_prompt": full_prompt,
            "estimated_tokens": total_tokens,
            "budget_limit": self.max_tokens,
            "was_compressed": (len(included_history) < len(conversation_history)),
            "retained_history_turns": len(included_history),
            "citations_preserved": len(retrieved_chunks),
        }
