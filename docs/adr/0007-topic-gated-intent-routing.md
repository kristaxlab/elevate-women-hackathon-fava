# Topic-gated Action Intent routing

Messages in Inbox, Smart Search, and Telegram General are classified for **Action Intent** (`save` | `search` | `unclear`) by the Intent Router. Forum topics still **gate execution**: save runs only in Inbox, search only in Smart Search; mismatches and unclear intents get plain-text notify only; General is nudge-only. Theme Topics stay silent.

This keeps topic affordances while catching wrong-topic and ambiguous messages, without a multi-agent runtime or open-web Smart Search.
