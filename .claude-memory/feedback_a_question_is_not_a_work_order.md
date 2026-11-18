---
name: feedback-a-question-is-not-a-work-order
description: "A plain question from the user gets a plain answer first; no scans, ticket edits, commits or multi-step plans unless asked."
metadata:
  node_type: memory
  type: feedback
  originSessionId: 802b7270-809d-4eda-825f-ca3f089bc865
  modified: 2026-11-18T17:55:42.917Z
---

When the user asks a question ("could X escalate?", "is that safe?"), answer it in a few lines from what is already known; a quick check is fine if the answer needs it. Do NOT turn it into work: no archive scans, no ticket appends, no commits, no "should I do 1-2-3?" plan.

**Why:** 2026-11-18, session 42: the user asked whether a deferred shape in T1-GU could "eventually escalate"; I ran a 627-run archive scan, appended to the ticket, committed, held the ticket open and proposed three steps. The user: "It was only an innocent question regarding escalation. I didn't expect you to escalate this escalation-question itself."

**How to apply:** answer, then at most one sentence offering the deeper look ("I can count it in the archive if you want"). Let the user decide whether it becomes work. Related: [[feedback_flag_ceremony_cost_early]], [[feedback_options_before_building_and_stop_weak_batches]].
