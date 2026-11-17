#!/usr/bin/env python3
"""T4-EZ: the REASON's words as the situation -- a model names the dilemma, structure and a reader check it.

Rule on the T4-EZ ticket (2026-11-17), written before labelling. The loop's own model and settings
(qwen3.8, local Ollama, temperature 0, reasoning none).

Steps (each cached under ~/.autobuilder/t4ez/):
  python3 scripts/t4ez_reasons.py cases     # extract cases with their reasons (t4ez_cases), cache
  python3 scripts/t4ez_reasons.py vocab     # step A: 150 sampled reasons -> phrases -> <= 15 kinds
  python3 scripts/t4ez_reasons.py label     # step B: every distinct reason -> one kind
  python3 scripts/t4ez_reasons.py check     # order agreement, structure agreement, 30 labels to read
  python3 scripts/t4ez_reasons.py count     # Q1-Q3 with (event kind, dilemma kind) as the situation
"""
import collections
import json
import os
import pickle
import random
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(__file__))
import t4ez_cases as tc  # noqa: E402

HOME = os.path.expanduser("~/.autobuilder/t4ez")
CASES = os.path.join(HOME, "cases.pkl")
VOCAB = os.path.join(HOME, "dilemma-kinds.json")
PHRASES = os.path.join(HOME, "dilemma-phrases.json")
LABELS = os.path.join(HOME, "reason-labels.json")
URL = "http://localhost:11434/v1/chat/completions"
MODEL = "qwen3.8"
MAX_CHARS = 1500

KIND_WORDS = {"rejected": "a proposed change was rejected by the evaluation",
              "closure refused": "closing a work item was refused by a check",
              "response discarded": "the model's answer could not be used"}


def ask(prompt):
    body = json.dumps({"model": MODEL, "temperature": 0, "reasoning_effort": "none",
                       "messages": [{"role": "user", "content": prompt}]}).encode()
    req = urllib.request.Request(URL, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(r.read())["choices"][0]["message"]["content"]


def json_in(text):
    m = re.search(r"(\[.*\]|\{.*\})", text, re.S)
    return json.loads(m.group(1)) if m else None


def load_cases():
    with open(CASES, "rb") as f:
        return pickle.load(f)


def distinct(cases):
    return sorted({(c["key"][0], c["reason"]) for c in cases if c["reason"].strip()})


# ---------------- step A ----------------

def vocab():
    cases = load_cases()
    rng = random.Random(41)
    by_kind = collections.defaultdict(list)
    for c in cases:
        if c["reason"].strip():
            by_kind[c["key"][0]].append((c["task"], c["reason"]))
    sample = []
    for kind, want in (("rejected", 90), ("closure refused", 35), ("response discarded", 25)):
        # spread over tasks: round-robin over tasks, distinct texts
        per_task = collections.defaultdict(list)
        for t, r in by_kind[kind]:
            per_task[t].append(r)
        for t in per_task:
            per_task[t] = sorted(set(per_task[t]))
            rng.shuffle(per_task[t])
        tasks = sorted(per_task)
        taken = set()
        while len([s for s in sample if s[0] == kind]) < want and any(per_task.values()):
            for t in tasks:
                if per_task[t]:
                    r = per_task[t].pop()
                    if r not in taken:
                        taken.add(r)
                        sample.append((kind, r))
                if len([s for s in sample if s[0] == kind]) >= want:
                    break
    phrases = []

    def name_batch(batch):
        lines = "\n".join(f"{j + 1}. ({KIND_WORDS[k]}) {r[:MAX_CHARS]}" for j, (k, r) in enumerate(batch))
        prompt = ("Below are reasons given while an automated system was building software. For each one, name the "
                  "DILEMMA the work faced -- what stood in the way, in general terms that would fit another project "
                  "too (no class, file or method names) -- in at most 8 words.\n"
                  "Answer with a JSON list of strings, one per reason, in order.\n\n" + lines)
        try:
            out = json_in(ask(prompt))
        except Exception:
            out = None
        if isinstance(out, dict):   # the model sometimes wraps the list: {"reasons": [...]}
            out = next((v for v in out.values() if isinstance(v, list)), None)
        return [str(x) for x in out] if isinstance(out, list) and len(out) == len(batch) else None
    for i in range(0, len(sample), 10):
        batch = sample[i:i + 10]
        got = name_batch(batch)
        if got is None:   # a count that does not match: one reason at a time
            got = []
            for one in batch:
                g = name_batch([one])
                got += g or []
        phrases += got
        print(f"batch {i // 10 + 1}: {len(got)} of {len(batch)} phrases", flush=True)
    json.dump(phrases, open(PHRASES, "w"), indent=1)
    prompt = ("These phrases each name a dilemma met while an automated system was building software:\n\n"
              + "\n".join(f"- {p}" for p in phrases)
              + "\n\nGroup them into AT MOST 15 kinds of dilemma. Each kind gets a short name (2-5 words) and a "
                "one-line definition that lets someone sort a new reason into it. Kinds must not overlap. "
                "Answer with a JSON list of objects {\"name\": ..., \"definition\": ...}.")
    kinds = json_in(ask(prompt)) or []
    json.dump(kinds, open(VOCAB, "w"), indent=1)
    for k in kinds:
        print(f"- {k['name']}: {k['definition']}")


# ---------------- step B ----------------

def label_prompt(kinds, event_kind, reason):
    listing = "\n".join(f"- {k['name']}: {k['definition']}" for k in kinds)
    return ("While an automated system was building software, " + KIND_WORDS[event_kind] + ". The reason given:\n\n"
            + reason[:MAX_CHARS] + "\n\nWhich ONE kind of dilemma does this reason describe? The kinds:\n"
            + listing + "\n- other: none of the above fits\n\n"
            "Answer with JSON only: {\"kind\": \"<the kind's name exactly as written>\"}")


def label_one(kinds, event_kind, reason):
    names = {k["name"] for k in kinds}
    try:
        got = (json_in(ask(label_prompt(kinds, event_kind, reason))) or {}).get("kind", "other")
    except Exception:
        got = "other"
    return got if got in names else "other"


def label():
    cases = load_cases()
    kinds = json.load(open(VOCAB))
    labels = json.load(open(LABELS)) if os.path.exists(LABELS) else {}
    todo = [(k, r) for k, r in distinct(cases) if json.dumps([k, r]) not in labels]
    print(f"distinct reasons {len(distinct(cases))}, to label {len(todo)}")
    for n, (k, r) in enumerate(todo, 1):
        labels[json.dumps([k, r])] = label_one(kinds, k, r)
        if n % 50 == 0:
            json.dump(labels, open(LABELS, "w"))
            print(f"  {n}/{len(todo)}", flush=True)
    json.dump(labels, open(LABELS, "w"))
    print(collections.Counter(labels.values()).most_common())


# ---------------- checks ----------------

def check():
    cases = load_cases()
    kinds = json.load(open(VOCAB))
    labels = json.load(open(LABELS))
    rng = random.Random(43)
    keys = sorted(labels)
    sample = rng.sample(keys, 100)
    rev = list(reversed(kinds))
    agree = sum(label_one(rev, *json.loads(k)) == labels[k] for k in sample)
    print(f"(1) order check: {agree}/100 labels unchanged with the kinds listed in reverse")
    comp = [k["name"] for k in kinds if re.search(r"compil", k["definition"], re.I) or re.search(r"compil", k["name"], re.I)]
    on = [c for c in cases if c["reason"].strip() and labels.get(json.dumps([c["key"][0], c["reason"]])) in comp
          and c["key"][0] == "rejected" and c["key"][1] != "-"]
    ok = sum(1 for c in on if c["key"][1] == "COMPILE_ERROR")
    print(f"(2) structure check: kinds naming a compile failure {comp}: {ok}/{len(on)} on probe COMPILE_ERROR"
          + (f" = {ok / len(on):.2f}" if on else ""))
    print("(3) 30 labels to read:")
    for k in rng.sample(keys, 30):
        ek, r = json.loads(k)
        print(f"   [{labels[k]}] ({ek}) {r[:230]!r}")


# ---------------- count ----------------

# The rule's one revision (2026-11-17): the order check gave 77/100; 11 of the 23 flips were between these two
# kinds, which both say "the code behaves wrongly". Merged, the same check reads 88/100.
MERGE = {"Breaking Existing Tests": "Wrong Behaviour (tests fail or logic is wrong)",
         "Logic and Implementation Errors": "Wrong Behaviour (tests fail or logic is wrong)"}


def count():
    cases = load_cases()
    labels = {k: MERGE.get(v, v) for k, v in json.load(open(LABELS)).items()}
    perms = 200
    rng = random.Random(23)
    cases = [c for c in cases if not c["action"].startswith("moved on (")]
    with_reason = [c for c in cases if c["reason"].strip()]
    print(f"cases (closed-before-move left out) {len(cases)}; with a reason {len(with_reason)}; "
          f"without {len(cases) - len(with_reason)} (left out of this count)")
    struct = {id(c): c["key"] for c in with_reason}
    for variant in ("(event kind, dilemma kind)", "(structural key, dilemma kind) -- reported, not deciding"):
        for c in with_reason:
            d = labels.get(json.dumps([struct[id(c)][0], c["reason"]]), "other")
            c["key"] = (struct[id(c)][0], d) if variant.startswith("(event") else (struct[id(c)], d)
        print(f"\n######## situation = {variant}: {len({c['key'] for c in with_reason})} keys")
        tk = collections.defaultdict(set)
        for c in with_reason:
            tk[c["key"]].add(c["task"])
        print(f"Q1 keys in >= 2 tasks: {sum(1 for v in tk.values() if len(v) >= 2)} of {len(tk)}; cases under them "
              f"{sum(1 for c in with_reason if len(tk[c['key']]) >= 2)} of {len(with_reason)}")
        rows = tc.q2(with_reason, rng, perms)
        held = [r for r in rows if r[2] >= 0.15 and r[3] < 0.05]
        print(f"Q2 keys with >= 20 cases and >= 2 actions of >= 5: {len(rows)}; gap >= 0.15 with p < 0.05: {len(held)}")
        for key, n, g, p, rs, nt in sorted(rows, key=lambda r: -r[1])[:25]:
            print(f"   {'*' if (g >= 0.15 and p < 0.05) else ' '} n={n:4d} tasks={nt:2d} gap={g:.2f} p={p:.3f} {str(key)[:110]}")
            print("        " + "; ".join(f"{a} {v:.2f}" for a, v in sorted(rs.items(), key=lambda kv: -kv[1])))
        keys = [c["key"] for c in with_reason]
        aware, blindg, n, ma, mb = tc.q3(with_reason, keys)
        diff = aware - blindg
        ge = 0
        for _ in range(perms):
            sh = keys[:]
            rng.shuffle(sh)
            a2, b2, _, _, _ = tc.q3(with_reason, sh)
            ge += (a2 - b2) >= diff
        p3 = (ge + 1) / (perms + 1)
        print(f"Q3 over {n} cases: aware {ma[0]:.3f} (n={ma[1]}) vs {ma[2]:.3f} (n={ma[3]}) gap {aware:+.3f}; "
              f"blind {mb[0]:.3f} (n={mb[1]}) vs {mb[2]:.3f} (n={mb[3]}) gap {blindg:+.3f}; "
              f"aware minus blind {diff:+.3f}, p={p3:.3f}")
        if variant.startswith("(event"):
            q2ok, q3ok = len(held) >= 3, diff >= 0.10 and p3 < 0.05
            print("DECISION per the rule:",
                  "a case memory is worth a slice" if (q2ok and q3ok) else
                  ("actions matter, but what worked does not carry across projects" if q2ok else
                   "actions do not measurably matter within situations (Q2 fails)"))


def main():
    os.makedirs(HOME, exist_ok=True)
    step = sys.argv[1] if len(sys.argv) > 1 else ""
    if step == "cases":
        cs = tc.load_cases()
        pickle.dump(cs, open(CASES, "wb"))
        print(f"{len(cs)} cases, {sum(1 for c in cs if c['reason'].strip())} with a reason, "
              f"{len(distinct(cs))} distinct reasons")
    elif step == "vocab":
        vocab()
    elif step == "label":
        label()
    elif step == "check":
        check()
    elif step == "count":
        count()
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
