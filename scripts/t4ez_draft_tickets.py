#!/usr/bin/env python3
"""T4-EZ: can Autobuilder draft its own next ticket from a run? Measured against the tickets we filed.

Rule on the T4-EZ ticket (2026-11-17), written before any draft. For each held-out run: the roadmap index as it
stood before the run's ticket was filed, a structural evidence digest of the run (no model), the loop's own model
(qwen3.8, temperature 0, reasoning none) asked for up to 5 draft tickets. Drafts, references and the prompt are
written to ~/.autobuilder/t4ez/drafts/<case>/ for scoring by hand.

Usage: python3 scripts/t4ez_draft_tickets.py [--case R1|R2|R3|C1|C2] [--digest-only] [--reasoning default]
"""
import collections
import glob
import gzip
import json
import os
import re
import subprocess
import sys
import urllib.request

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.expanduser("~/.autobuilder/t4ez/drafts")
ARCHIVE = os.path.expanduser("~/.autobuilder/run-archive")
URL = "http://localhost:11434/v1/chat/completions"
MODEL = "qwen3.8"
EVIDENCE_BUDGET = 36000   # characters; the roadmap index takes ~50k, the window is 32k tokens with 4k reserved

CASES = {
    "R1": {"dir": "data/pantry-rev3", "behaviour": "logs/behaviour.ndjson", "roadmap_at": "efd457fa^",
           "refs": {"T1-GM": "efd457fa", "T4-EU": "efd457fa"}},
    "R2": {"dir": "data/gradient", "behaviour": "logs/behaviour.ndjson", "roadmap_at": "3825d270^",
           "refs": {"T1-GP": "3825d270", "T1-GQ": "3825d270"}},
    "R3": {"dir": "data/gradient-t1gq2", "behaviour": "logs/behaviour-2026-11-16_17-01-01.ndjson",
           "roadmap_at": "84838fd7^", "refs": {"T1-GR": "84838fd7", "T2-FN": "84838fd7", "T4-EY": "3ab0081a"}},
    "C1": {"dir": "data/pantry-rev5", "behaviour": "logs/behaviour.ndjson", "roadmap_at": "efd457fa^", "refs": {}},
    "C2": {"archive": "2026-10-06_15-15-22", "roadmap_at": None, "refs": {}},
    # the first full ticket cycle's run (stuck-alternation, 2026-11-17 21:23); scored against Claude's diagnosis,
    # written before any draft (drafts/F1/reference-claude-diagnosis.md)
    "F1": {"dir": "data/stuck-alternation", "behaviour": "logs/behaviour.ndjson", "roadmap_at": "5ec46957", "refs": {}},
    # the second run (build with T1-GS, 2026-11-17 22:50); F1's files were moved to ~/.autobuilder/field-forward2/prev
    "F2": {"dir": "data/stuck-alternation", "behaviour": "logs/behaviour.ndjson", "roadmap_at": "6debd647", "refs": {}},
}


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True, check=True).stdout


def roadmap_at_date(date):
    c = git("rev-list", "-1", f"--before={date}", "HEAD", "--", ".claude-memory/roadmap.md").strip()
    return c


# ---------------- roadmap ----------------

TICKET = re.compile(r"^- \[(.)\] \*\*(T\d-[A-Z0-9-]+?)(?::|\s)\s*(.*?)\*\*", re.M)


def roadmap_index(commit):
    text = git("show", f"{commit}:.claude-memory/roadmap.md")
    lines = []
    for m in TICKET.finditer(text):
        mark, tid, title = m.group(1), m.group(2), m.group(3)
        status = {"x": "closed", " ": "open", "~": "deferred", "-": "dropped"}.get(mark, mark)
        lines.append(f"{tid} [{status}] {title[:90]}")
    return lines


def reference(commit, tid):
    text = git("show", f"{commit}:.claude-memory/roadmap.md")
    m = re.search(r"^- \[.\] \*\*" + re.escape(tid) + r"[:\s].*?(?=^- \[.\] \*\*T|\Z)", text, re.M | re.S)
    return m.group(0).strip() if m else None


# ---------------- evidence digest (structural, no model) ----------------

def events_from(path):
    opener = gzip.open if path.endswith(".gz") else open
    t = opener(path, "rt", errors="replace").read()
    d = json.JSONDecoder()
    i = 0
    while i < len(t):
        while i < len(t) and t[i].isspace():
            i += 1
        if i >= len(t):
            break
        try:
            o, i = d.raw_decode(t, i)
        except ValueError:
            break
        yield o


def cut(s, n):
    s = re.sub(r"\s+", " ", str(s or "")).strip()
    return s if len(s) <= n else s[:n] + " ..."


def hhmm(e):
    return (e.get("timestamp") or "")[11:19]


def digest(case):
    if "archive" in case:
        d = os.path.join(ARCHIVE, case["archive"])
        evs = list(events_from(os.path.join(d, "behaviour.ndjson.gz")))
        meta = {"stuck": os.path.join(d, "stuck-ledger.json")}
    else:
        d = os.path.join(ROOT, case["dir"])
        evs = list(events_from(os.path.join(d, case["behaviour"])))
        meta = {"stuck": os.path.join(d, "meta/stuck-ledger.json"), "report": os.path.join(d, "meta/run-end-report.json"),
                "inputs": os.path.join(d, "meta/inputs.json"), "landing": glob.glob(os.path.join(d, "meta/inputs/*/landing.json"))}
    parts = []
    by = collections.defaultdict(list)
    for e in evs:
        by[e.get("event")].append(e)
    rs = (by.get("run_started") or [{}])[0]
    parts.append("RUN: started " + (rs.get("timestamp") or "?")[:19] + " " + json.dumps(rs.get("payload") or {}))
    ends = by.get("cycle_end") or []
    parts.append(f"COUNTS: cycles {len(by.get('cycle_start', []))}, merges {len(by.get('mutation_merged', []))}, "
                 f"rejections {len(by.get('mutation_rejected', []))}, closure refusals {len(by.get('closure_refused', []))}, "
                 f"last cycle result {(ends[-1].get('payload') or {}).get('resultState') if ends else '?'} at {hhmm(ends[-1]) if ends else '?'}")
    if meta.get("report") and os.path.exists(meta["report"]):
        rep = json.load(open(meta["report"]))
        parts.append("RUN-END REPORT: " + cut(json.dumps(rep), 3000))
    halts = [e for k in ("system_paused", "rewind_performed", "guard_violated", "branch_forked", "branch_sealed",
                         "branch_adopted") for e in by.get(k, [])]
    halts.sort(key=lambda e: e.get("timestamp") or "")
    if halts:
        parts.append("PAUSES, REWINDS, GUARDS, BRANCHES:\n" + "\n".join(
            f"  {hhmm(e)} {e['event']}: {cut(json.dumps(e.get('payload')), 300)}" for e in halts[:40]))
    dr = by.get("diagnosis_request", [])
    if dr:
        parts.append("DIAGNOSIS REQUESTS:\n" + "\n".join(f"  {hhmm(e)} {cut(json.dumps(e.get('payload')), 500)}" for e in dr[:12]))
    if os.path.exists(meta["stuck"]):
        st = json.load(open(meta["stuck"]))
        lines = []
        for s in st[:15]:
            qa = " | ".join(f"Q{q.get('rung')}: {cut(q.get('question'), 120)} -> {cut(q.get('answer'), 300)}"
                            for q in (s.get("questions") or []))
            lines.append(f"  [{s.get('source')}, {s.get('status')}, x{s.get('occurrences')}] {cut(s.get('signature'), 300)}"
                         + (f"\n     {qa}" if qa else ""))
        parts.append("STUCK LEDGER:\n" + "\n".join(lines))
    cr = collections.OrderedDict()
    for e in by.get("closure_refused", []):
        r = cut((e.get("payload") or {}).get("reason"), 400)
        cr.setdefault(r, []).append(hhmm(e))
    if cr:
        parts.append("CLOSURE REFUSALS (distinct, count, first-last):\n" + "\n".join(
            f"  x{len(ts)} {ts[0]}-{ts[-1]}: {r}" for r, ts in cr.items()))
    rj = collections.Counter(cut((e.get("payload") or {}).get("reason"), 350) for e in by.get("mutation_rejected", []))
    if rj:
        parts.append("MOST FREQUENT REJECTION REASONS:\n" + "\n".join(f"  x{n}: {r}" for r, n in rj.most_common(12)))
    if meta.get("inputs") and os.path.exists(meta["inputs"]):
        inp = json.load(open(meta["inputs"]))
        il = [f"  input {i.get('sequence')} {i.get('kind')} by {i.get('addedBy')}: {cut(i.get('content'), 200)}"
              for i in inp.get("inputs", [])]
        el = [f"  {ev.get('kind')} '{ev.get('subject')}': receiving '{cut(ev.get('receiving'), 120)}' / other "
              f"'{cut(ev.get('other'), 120)}' -> {ev.get('status')} {ev.get('decision')} by {ev.get('by')}: "
              f"'{cut(ev.get('resolution'), 200)}'; lines retired: {[cut(x, 100) for x in (ev.get('replaces') or [])]}"
              for ev in inp.get("events", [])]
        parts.append("PROJECT INPUTS:\n" + "\n".join(il) + ("\nINPUT DECISIONS:\n" + "\n".join(el) if el else ""))
    for lp in meta.get("landing") or []:
        ld = json.load(open(lp))
        crit = ld.get("criteria") or []
        parts.append(f"LANDING OF A BROUGHT PROJECT: {len(crit)} criteria carried into the import's goal:\n"
                     + "\n".join(f"  - {cut(c, 200)}" for c in crit[:10]))
    nar = by.get("cycle_narrated", [])
    if nar:
        step = max(1, len(nar) // 40)
        parts.append("NARRATOR (what the run told, in order):\n" + "\n".join(
            f"  {hhmm(e)} {cut((e.get('payload') or {}).get('text'), 260)}" for e in nar[::step][:40]))
    text = "\n\n".join(parts)
    return text if len(text) <= EVIDENCE_BUDGET else text[:EVIDENCE_BUDGET] + "\n[evidence cut to fit]"


# ---------------- the ask ----------------

PROMPT = """You are the development lead of Autobuilder, a system that evolves software projects with language
models. Below is (1) Autobuilder's roadmap index -- every ticket with its ID, status and title -- and (2) the
evidence of one run Autobuilder just finished on a target project.

Your job: decide what Autobuilder's OWN code should change, based on this run. Find misbehaviour of Autobuilder
itself (not of the target project's code): a check that judged wrongly, a mechanism that undid or ignored another,
work repeated without progress, a decision recorded wrongly, requirements that should not have been in force, a
missing capability the run needed. Quote the evidence lines that show it.

For each finding write a draft ticket. If an existing ticket in the index already covers it, give its ID instead
of a new ticket. If the run shows nothing Autobuilder should change, say so -- do not invent work.

Answer with JSON only:
{"drafts": [{"title": "...", "observed": "... (quote evidence)", "suspected_cause": "...",
             "tier": "T1 correctness bug | T2 policy | T4 missing capability | T5 observability",
             "existing_ticket": "<ID or null>", "definition_of_done": "..."}],
 "nothing_to_file": false}
At most 5 drafts.

=== ROADMAP INDEX ===
{roadmap}

=== RUN EVIDENCE ===
{evidence}
"""


def ask(prompt, reasoning):
    body = {"model": MODEL, "temperature": 0, "messages": [{"role": "user", "content": prompt}]}
    if reasoning != "default":
        body["reasoning_effort"] = "none"
    req = urllib.request.Request(URL, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=1800) as r:
        return json.loads(r.read())


def run_case(name, digest_only, reasoning):
    case = CASES[name]
    out = os.path.join(OUT, name + ("" if reasoning == "none" else "-reasoning"))
    os.makedirs(out, exist_ok=True)
    at = case["roadmap_at"]
    if at is None:   # a control from the archive: the roadmap as it stood at the run's date
        at = roadmap_at_date(case["archive"][:10] + " " + case["archive"][11:].replace("-", ":"))
    index = roadmap_index(at)
    ev = digest(case)
    prompt = PROMPT.replace("{roadmap}", "\n".join(index)).replace("{evidence}", ev)
    open(os.path.join(out, "prompt.txt"), "w").write(prompt)
    for tid, commit in case["refs"].items():
        open(os.path.join(out, f"reference-{tid}.md"), "w").write(reference(commit, tid) or "(not found)")
    print(f"{name}: roadmap at {at}: {len(index)} tickets ({sum(map(len, index))} chars); evidence {len(ev)} chars; "
          f"prompt {len(prompt)} chars; leaked reference IDs in the index: "
          f"{[t for t in case['refs'] if any(l.startswith(t + ' ') for l in index)]}")
    if digest_only:
        return
    resp = ask(prompt, reasoning)
    content = resp["choices"][0]["message"]["content"]
    usage = resp.get("usage") or {}
    open(os.path.join(out, "answer.txt"), "w").write(content)
    json.dump(usage, open(os.path.join(out, "usage.json"), "w"))
    print(f"   usage {usage}")
    m = re.search(r"\{.*\}", content, re.S)
    try:
        drafts = json.loads(m.group(0)) if m else None
    except ValueError:
        drafts = None
    print(json.dumps(drafts, indent=1)[:6000] if drafts else content[:4000])


CHECK = """While Autobuilder (a system that evolves software projects with language models) ran on a target project,
a reviewer read the run's evidence and drafted the ticket below, claiming Autobuilder's OWN code misbehaved.

Your job is to check the draft against the evidence -- nothing else. Keep it only if the evidence bears out BOTH:
(a) the observed misbehaviour actually happened as described (quotes are real and read correctly: a status, a
count, a time, what came before what), and
(b) it is a fault of Autobuilder -- not a check correctly refusing incomplete work, not the target's own code
being unfinished, not a mechanism doing what it is designed to do, not a problem that ended by itself.
If either is not borne out, drop it.

=== DRAFT ===
{draft}

=== RUN EVIDENCE ===
{evidence}

Answer with JSON only: {"verdict": "keep" or "drop", "why": "<one or two sentences>"}"""


def parse_drafts(text):
    m = re.search(r"\{.*\}", text or "", re.S)
    try:
        return (json.loads(m.group(0)) if m else {}).get("drafts") or []
    except ValueError:
        return []


def check_case(name):
    case = CASES[name]
    out = os.path.join(OUT, name)
    ev = digest(case)
    drafts = parse_drafts(open(os.path.join(out, "answer.txt")).read())
    orig = parse_drafts(open(os.path.join(OUT, "arm1-original", name + "-answer.txt")).read())
    same = [d.get("title") for d in drafts] == [d.get("title") for d in orig]
    print(f"{name}: {len(drafts)} drafts; identical titles to arm 1: {same}")
    results = []
    for i, d in enumerate(drafts, 1):
        draft = json.dumps({k: d.get(k) for k in ("title", "observed", "suspected_cause")}, indent=1)
        try:
            resp = ask(CHECK.replace("{draft}", draft).replace("{evidence}", ev), "default")
            content = resp["choices"][0]["message"]["content"] or ""
            usage = resp.get("usage") or {}
            m = re.search(r"\{.*\}", content, re.S)
            v = json.loads(m.group(0)) if m else {}
        except Exception as ex:   # no answer = drop, reported
            v, usage = {"verdict": "drop", "why": f"no usable answer ({ex})"}, {}
        verdict = v.get("verdict") if v.get("verdict") in ("keep", "drop") else "drop"
        results.append({"n": i, "title": d.get("title"), "verdict": verdict, "why": v.get("why"), "usage": usage})
        print(f"   #{i} {verdict.upper():4s} {d.get('title', '')[:90]}\n        why: {str(v.get('why'))[:300]}  "
              f"[tokens {usage.get('prompt_tokens')}+{usage.get('completion_tokens')}]", flush=True)
    json.dump(results, open(os.path.join(out, "check.json"), "w"), indent=1)


READ_QUESTION = """A reviewer read the evidence of one Autobuilder run and drafted the ticket below, claiming that
Autobuilder's OWN code misbehaved (Autobuilder evolves software projects with language models; the logs are its
logs, the project files are the target project it worked on).

Check the draft against what the logs and the files actually show. Keep it only if your probes bear out BOTH:
(a) the observed misbehaviour actually happened as described (what came before what, a status, a count, what a
file or test really contains), and
(b) it is a fault of Autobuilder. A check or mechanism doing what it was built to do can still be the fault: if
its verdict or effect was wrong given what actually happened (it judged something it misread, it undid another
mechanism's decision, it kept a requirement that should not hold), that counts. Drop only when your probes show
the behaviour was right given the facts, or the claim is not borne out.

DRAFT:
{draft}

Your #Answer must begin with the word KEEP or DROP, then the reason, citing the log lines or file lines it rests on."""

READ_SOURCES = {
    "R1": {"dir": "data/pantry-rev3", "behaviour": "logs/behaviour.ndjson", "main": "logs/autobuilder.log"},
    "R2": {"dir": "data/gradient", "behaviour": "logs/behaviour.ndjson", "main": "logs/autobuilder.log"},
    "R3": {"dir": "data/gradient-t1gq2", "behaviour": "logs/behaviour-2026-11-16_17-01-01.ndjson",
           "main": "logs/autobuilder-2026-11-16_17-00-58.log"},
    "C1": {"dir": "data/pantry-rev5", "behaviour": "logs/behaviour.ndjson", "main": "logs/autobuilder.log"},
    "C2": {"archive": "2026-10-06_15-15-22"},
    "F1": {"dir": "data/stuck-alternation", "behaviour": "logs/behaviour.ndjson", "main": "logs/autobuilder.log"},
    "F2": {"dir": "data/stuck-alternation", "behaviour": "logs/behaviour.ndjson", "main": "logs/autobuilder.log"},
}
MAIN_TS = re.compile(r"^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)")


def prepare_reading(only=None, suffix=""):
    base = os.path.expanduser("~/.autobuilder/t4ez/check-inputs")
    corpus = []
    for name, src in READ_SOURCES.items():
        if only and name not in only:
            continue
        d = os.path.join(base, name)
        os.makedirs(d, exist_ok=True)
        if "archive" in src:
            a = os.path.join(ARCHIVE, src["archive"])
            beh = gzip.open(os.path.join(a, "behaviour.ndjson.gz"), "rt", errors="replace").read()
            main = gzip.open(os.path.join(a, "autobuilder.log.gz"), "rt", errors="replace").read()
            tree = os.path.join(d, "tree")
            os.makedirs(tree, exist_ok=True)
            subprocess.run(["tar", "-xzf", os.path.join(a, "artifact-src.tar.gz"), "-C", tree], check=True)
            extra = {}
        else:
            pd = os.path.join(ROOT, src["dir"])
            beh = open(os.path.join(pd, src["behaviour"]), errors="replace").read()
            main = open(os.path.join(pd, src["main"]), errors="replace").read()
            tree = os.path.join(pd, "output")
            extra = {"inputs.json": os.path.join(pd, "meta/inputs.json")}
            for i, lp in enumerate(glob.glob(os.path.join(pd, "meta/inputs/*/landing.json"))):
                extra[f"landing-{i + 1}.json"] = lp
        stamps = re.findall(r'"timestamp" : "([^"]+)"', beh)
        end = max(stamps)[:19].replace("T", " ") if stamps else "9999"
        kept, cut_at = [], None
        for line in main.split("\n"):
            m = MAIN_TS.match(line)
            if m and m.group(1) > end:
                cut_at = m.group(1)
                break
            kept.append(line)
        logs = {"behaviour.ndjson": os.path.join(d, "behaviour.ndjson"), "autobuilder.log": os.path.join(d, "autobuilder.log")}
        open(logs["behaviour.ndjson"], "w").write(beh)
        open(logs["autobuilder.log"], "w").write("\n".join(kept))
        for k, v in extra.items():
            if os.path.exists(v):
                logs[k] = v
        print(f"{name}: run ends {end}; main log cut at {cut_at} ({len(kept)} lines kept); tree {tree}; logs {list(logs)}")
        for i, dr in enumerate(parse_drafts(open(os.path.join(OUT, name, "answer.txt")).read()), 1):
            draft = json.dumps({k: dr.get(k) for k in ("title", "observed", "suspected_cause")}, indent=1)
            corpus.append({"id": f"{name}#{i}", "question": READ_QUESTION.replace("{draft}", draft),
                           "logs": logs, "tree": tree})
    path = os.path.join(base, f"corpus{suffix}.json")
    json.dump(corpus, open(path, "w"), indent=1)
    print(f"{len(corpus)} questions -> {path}")


DECISIONS_QUESTION = """A reviewer read the evidence of one Autobuilder run and drafted the ticket below, claiming that
Autobuilder's OWN code misbehaved (Autobuilder evolves software projects with language models; the logs are its
logs, the project files are the target project it worked on, roadmap.md is Autobuilder's own roadmap of tickets and
decisions as it stood then, digest.txt is the summary of the run the reviewer read).

Check the draft. Keep it only if BOTH hold:
(a) the observed misbehaviour actually happened as described -- check it in the logs, the files or digest.txt
(what came before what, a status, a count, what a file or test really contains);
(b) it is a fault, not the intended design. Whether it is a fault or the intended design is decided by the
project's RECORDED DECISIONS, not by your opinion: search roadmap.md for the ticket or rule that governs this
behaviour. If a decision made the behaviour so on purpose and the run does not show that decision's own premise
failing, it is the design: DROP. If the behaviour contradicts a recorded decision, or its effect was wrong given
the facts and no decision makes it intended, it is a fault: KEEP. If a ticket already covers it, KEEP and name it.

DRAFT:
{draft}

Your #Answer must begin with the word KEEP or DROP, then the reason, citing the log, file and roadmap.md lines it
rests on."""


def prepare_decisions(suffix=""):
    base = os.path.expanduser("~/.autobuilder/t4ez/check-inputs")
    reading = json.load(open(os.path.join(base, f"corpus{suffix}.json")))
    out = []
    for q in reading:
        name = q["id"].split("#")[0]
        case = CASES[name]
        d = os.path.join(base, name)
        at = case["roadmap_at"] or roadmap_at_date(case["archive"][:10] + " " + case["archive"][11:].replace("-", ":"))
        rp, dp = os.path.join(d, "roadmap.md"), os.path.join(d, "digest.txt")
        if not os.path.exists(rp):
            open(rp, "w").write(git("show", f"{at}:.claude-memory/roadmap.md"))
            open(dp, "w").write(digest(case))
        draft = q["question"].split("DRAFT:\n", 1)[1].split("\n\nYour #Answer", 1)[0]
        logs = dict(q["logs"], **{"roadmap.md": rp, "digest.txt": dp})
        out.append({"id": q["id"], "question": DECISIONS_QUESTION.replace("{draft}", draft), "logs": logs, "tree": q["tree"]})
    path = os.path.join(base, f"corpus-decisions{suffix}.json")
    json.dump(out, open(path, "w"), indent=1)
    print(f"{len(out)} questions -> {path}; sources per question: {list(out[0]['logs'])}")


def main():
    only = [sys.argv[sys.argv.index("--case") + 1]] if "--case" in sys.argv else None
    suffix = "-" + only[0] if only else ""
    if "--prepare-decisions" in sys.argv:
        prepare_decisions(suffix)
        return
    if "--prepare-reading" in sys.argv:
        prepare_reading(only, suffix)
        return
    if "--check" in sys.argv:
        names = [sys.argv[sys.argv.index("--case") + 1]] if "--case" in sys.argv else list(CASES)
        for n in names:
            check_case(n)
        return
    names = [sys.argv[sys.argv.index("--case") + 1]] if "--case" in sys.argv else list(CASES)
    reasoning = sys.argv[sys.argv.index("--reasoning") + 1] if "--reasoning" in sys.argv else "none"
    for n in names:
        run_case(n, "--digest-only" in sys.argv, reasoning)


if __name__ == "__main__":
    main()
