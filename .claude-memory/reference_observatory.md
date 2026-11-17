---
name: reference-observatory
description: "The Observatory = git submodule `observatory/` (autobuilder-observatory repo): local read-only dashboard over roadmap, tickets, git, test reports and live run events; port 8090."
metadata:
  node_type: memory
  type: reference
  originSessionId: a093fdc9-3d89-4999-bb88-9e803f989141
  modified: 2026-11-17T18:45:57.351Z
---

`observatory/` in the Autobuilder repo is a git SUBMODULE (github michael-sandy-walker/autobuilder-observatory,
last commit 2026-11-07 "Local server, run metadata reader and live view, with tests"). Start:
`python3 observatory/server.py` -> http://127.0.0.1:8090 (loopback only; Python 3.10+, no packages).

What it reads (read-only; never mutates projects, never calls an LLM):
- roadmap and tickets via `scripts/next_work.py` (`/api/snapshot`, `/api/tickets/<ID>` with source line);
- git history and working tree (`meta.py`), commit-to-ticket links FROM COMMIT TITLES, saved Surefire XML reports
  (may be stale -- see [[feedback-read-test-results-from-the-run]]), an allowlist of design/process documents;
- live run events proxied from the run context (8081) and home context (8080).

Missing as of 2026-11-17: a link from a ticket to the RUN(s) that exercised it.

The user calls it "the Observatory"; ChatGPT proposals (2026-11-17) used it as the entry point for a
ticket -> run -> tests -> status loop under [[project-outer-loop-inward-design-note]] (T4-EZ). The project's
`.claude-memory` notes did not mention it before this date.
