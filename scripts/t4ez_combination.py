#!/usr/bin/env python3
"""T4-EZ: do COMBINATIONS of similarity lenses predict how a stuck problem ends, better than one lens?

Rule on the T4-EZ ticket (2026-11-17), written before counting. One row per contentful stuck entry in the run
archive; features only as known at the first question; three targets (ended / slow / fine fix form); two
evaluations (time-ordered, leave-one-task-out); lift over the training majority; permutation p refitting the
whole search.

Usage: python3 scripts/t4ez_combination.py [--perms N] [--control-only] [--rows]
"""
import bisect
import collections
import gzip
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
import t4ez_shape_recurrence as shp  # noqa: E402
import t4ez_cause_fix as cf  # noqa: E402

SOURCES = {"BUILD", "TEST_FAILURES", "CLOSURE_REFUSAL", "BOOT"}


def num(cycle):
    m = re.match(r"cycle-(\d+)", cycle or "")
    return int(m.group(1)) if m else None


def events(path):
    t = gzip.open(path, "rt", errors="replace").read()
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


def goals_of(run_dir):
    p = os.path.join(run_dir, "GoalStack.json.gz")
    out = {}
    try:
        gs = json.load(gzip.open(p))
    except Exception:
        return out

    def walk(x):
        if isinstance(x, dict):
            if "id" in x and ("description" in x or "type" in x):
                out[x["id"]] = x
            for v in x.values():
                walk(v)
        elif isinstance(x, list):
            for v in x:
                walk(v)
    walk(gs)
    return out


# ---------------- fine fix form ----------------

METHOD_DECL = re.compile(r"^\s*(?:@\w+\s+)*(?:(?:public|private|protected|static|final|abstract|default|synchronized)\s+)*"
                         r"[\w<>\[\],.? ]+\s+(\w+)\s*\(")


def listed_members(e, missing):
    a = ((e.get("questions") or [{}])[0].get("answer") or "")
    names = set(re.findall(r"\b(\w+)\(", a)) - {missing}
    return names


def fine_fix(e, writes):
    if writes is None:
        return "no log"
    m = re.match(r"cycle-\d+", e.get("endedCycle") or "")
    ws = writes.get(m.group(0) if m else None) or []
    if not ws:
        return "no write"
    if e["source"] != "BUILD":
        paths = [p for p, _, _, _ in ws]
        test = [p for p in paths if "/src/test/" in "/" + p]
        conf = [p for p in paths if not p.endswith(".java")]
        main = [p for p in paths if p not in test and p not in conf]
        if conf and not test and not main:
            return "build configuration"
        return "test and code" if test and main else ("test only" if test else "code only")
    name, _ = cf.named(e)
    if name:
        word = re.compile(r"\b" + re.escape(name) + r"\b")
        if any(cf.declares(a, name) for _, _, _, a in ws):
            return "name declared"
        replaced = [(p, whole, r, a) for p, whole, r, a in ws
                    if (not whole and any(word.search(x) for x in r) and not any(word.search(x) for x in a))]
        sig_file = re.search(r"(\w+)\.java:", e["signature"])
        replaced += [(p, whole, r, a) for p, whole, r, a in ws
                     if whole and not any(word.search(x) for x in a)
                     and (sig_file is None or os.path.basename(p) == sig_file.group(1) + ".java")]
        if replaced:
            listed = listed_members(e, name)
            if listed and any(re.search(r"\b(" + "|".join(map(re.escape, listed)) + r")\s*\(", x)
                              for _, _, _, a in replaced for x in a):
                return "replaced by a listed member"
            return "replaced otherwise"
    imp = lambda xs: sorted(x.strip() for x in xs if x.strip().startswith("import "))  # noqa: E731
    if any(imp(r) != imp(a) for _, _, r, a in ws):
        return "import changed"
    if any(any(x.strip().startswith("@") and x.strip() not in {y.strip() for y in r} for x in a) for _, _, r, a in ws):
        return "annotation added"
    for _, _, r, a in ws:
        # a hunk's SEARCH and REPLACE carry unchanged context: only a declaration line that differs counts
        a_lines = {x.strip() for x in a}
        an = {METHOD_DECL.match(x).group(1) for x in a if METHOD_DECL.match(x) and not x.strip().startswith("return")}
        if any(METHOD_DECL.match(x) and not x.strip().startswith(("return", "throw", "new "))
               and x.strip() not in a_lines and METHOD_DECL.match(x).group(1) in an for x in r):
            return "a declaration's signature changed"
    if any(whole for _, whole, _, _ in ws):
        return "file written whole"
    return "body edit"


# ---------------- rows ----------------

def mode_of(rs):
    p = (rs or {}).get("payload") or {}
    if p.get("situation") == "FOREIGN":
        return "adopted foreign"
    if p.get("mode") == "NEW_PROJECT" and p.get("situation") == "EMPTY":
        return "new"
    return "loop-authored"


def build_rows():
    rows = []
    seen_exact = collections.defaultdict(set)   # task -> signatures seen in earlier runs
    for run, target, entries in shp.load():
        task = shp.task_of(target)
        mine = [e for e in entries if e["source"] in SOURCES]
        if not mine:
            seen_exact[task] |= {e["signature"] for e in entries}
            continue
        d = os.path.join(cf.ARCHIVE, run)
        evs = list(events(os.path.join(d, "behaviour.ndjson.gz"))) if os.path.exists(
            os.path.join(d, "behaviour.ndjson.gz")) else []
        rs = next((x for x in evs if x.get("event") == "run_started"), None)
        starts = sorted({num(x["cycleId"]) for x in evs if x.get("event") == "cycle_start" and num(x["cycleId"])})
        goals = goals_of(d)
        owns = cf.own_types(d)
        writes = cf.writes_of(d)
        wcycles = sorted((num(c), c) for c in (writes or {}) if num(c))
        spans = [(num(e["firstCycle"]), num(e.get("endedCycle")) or num(e["lastCycle"])) for e in entries]
        for e in mine:
            first = num(e["firstCycle"])
            # course at onset
            concurrent = any(s is not None and s < first and (t or 0) >= first for s, t in spans)
            earlier = any(s is not None and s < first for s, t in spans)
            prior_exact = e["signature"] in seen_exact[task]
            # context at onset
            g = goals.get(e.get("subGoalId"))
            if g is None:
                sub = "unknown"
            else:
                sub = "repair" if (g.get("metadata") or {}).get("blockerType") else "behavioural"
            fmt = "none"
            if writes is not None:
                before = [c for n, c in wcycles if n <= first]
                if before:
                    fmt = "whole" if any(w for _, w, _, _ in writes[before[-1]]) else "hunks"
            sf = re.search(r"([\w/]+)\.java", e["signature"])
            sig_file = ("test" if sf and ("Test" in sf.group(1) or "/test/" in sf.group(1)) else "main") if sf else "none"
            first_only = dict(e, questions=(e.get("questions") or [])[:1])
            ended = e.get("status") == "ENDED"
            cycles = None
            if ended and starts:
                cycles = bisect.bisect_right(starts, num(e["endedCycle"])) - bisect.bisect_left(starts, first)
            rows.append({
                "run": run, "task": task, "family": shp.family_of(task), "signature": e["signature"],
                "shape": shp.shape(e), "source": e["source"],
                "cause": cf.cause(first_only, owns),
                "course": (concurrent, earlier, prior_exact),
                "context": (mode_of(rs), sub, fmt, sig_file),
                "ended": ended, "cycles": cycles,
                "fix": fine_fix(e, writes) if ended else None,
                "entry": e,
            })
        seen_exact[task] |= {e["signature"] for e in entries}
    return rows


# ---------------- predictors ----------------

PREDICTORS = {
    "shape": [("shape",)],
    "cause": [("cause",)],
    "course": [("course",)],
    "context": [("context",)],
    "cause x context": [("cause", "context"), ("cause",)],
    "cause x course": [("cause", "course"), ("cause",)],
    "shape x context": [("shape", "context"), ("shape",)],
    "cause x context x course": [("cause", "context", "course"), ("cause", "context"), ("cause",)],
}
SINGLE = ("shape", "cause", "course", "context")
REFERENCE = "source alone (reference, not in the rule)"
PREDICTORS[REFERENCE] = [("source",)]
MIN_SUPPORT = 3


def key(r, fields):
    return tuple(r[f] for f in fields)


def fit(train, y):
    tables = {}
    for name, levels in PREDICTORS.items():
        tables[name] = [collections.defaultdict(collections.Counter) for _ in levels]
        for r, t in zip(train, y):
            for i, fields in enumerate(levels):
                tables[name][i][key(r, fields)][t] += 1
    maj = collections.Counter(y).most_common(1)[0][0]
    return tables, maj


def predict(tables, maj, name, r):
    for i, fields in enumerate(PREDICTORS[name]):
        c = tables[name][i].get(key(r, fields))
        if c and sum(c.values()) >= MIN_SUPPORT:
            return c.most_common(1)[0][0]
    return maj


def evaluate(rows, ys, folds):
    """folds: list of (train_idx, test_idx). -> {name: lift}, baseline accuracy, n"""
    correct = collections.Counter()
    base = 0
    n = 0
    for tr, te in folds:
        if not tr or not te:
            continue
        tables, maj = fit([rows[i] for i in tr], [ys[i] for i in tr])
        for i in te:
            n += 1
            base += ys[i] == maj
            for name in PREDICTORS:
                correct[name] += predict(tables, maj, name, rows[i]) == ys[i]
    if n == 0:
        return {}, float("nan"), 0
    return {k: (correct[k] - base) / n for k in PREDICTORS}, base / n, n


def folds_time(rows):
    runs = sorted({r["run"] for r in rows})
    cut = set(runs[: int(len(runs) * 0.7)])
    return [([i for i, r in enumerate(rows) if r["run"] in cut], [i for i, r in enumerate(rows) if r["run"] not in cut])]


def folds_task(rows):
    tasks = sorted({r["task"] for r in rows})
    return [([i for i, r in enumerate(rows) if r["task"] != t], [i for i, r in enumerate(rows) if r["task"] == t])
            for t in tasks]


def control(rows):
    r = next(r for r in rows if r["run"] == "2026-10-11_07-34-41" and "getComponents()" in r["signature"] and r["ended"])
    print(f"control: '{r['signature']}' -> {r['fix']}")
    return r["fix"] == "replaced by a listed member"


def main():
    perms = int(sys.argv[sys.argv.index("--perms") + 1]) if "--perms" in sys.argv else 200
    rows = build_rows()
    if not control(rows):
        print("CONTROL FAILED -- fix the extractor before counting")
        sys.exit(1)
    if "--control-only" in sys.argv:
        return
    ended = [r for r in rows if r["ended"]]
    med = sorted(r["cycles"] for r in ended if r["cycles"] is not None)
    med = med[len(med) // 2] if med else None
    targets = {
        "T1 ended": (rows, [r["ended"] for r in rows]),
        "T2 slow": ([r for r in ended if r["cycles"] is not None],
                    [r["cycles"] > med for r in ended if r["cycles"] is not None]),
        "T3 fix form": ([r for r in ended if r["fix"] not in ("no log",)],
                        [r["fix"] for r in ended if r["fix"] not in ("no log",)]),
    }
    print(f"rows {len(rows)} ({len(ended)} ENDED); tasks {len({r['task'] for r in rows})}; "
          f"median cycles to end {med}")
    print("fine fix forms:", collections.Counter(r["fix"] for r in ended).most_common())
    print("causes (first answer only):", len({r['cause'] for r in rows}), " shapes:", len({r['shape'] for r in rows}),
          " course keys:", len({r['course'] for r in rows}), " context keys:", len({r['context'] for r in rows}))
    rng = random.Random(17)
    verdicts = {}
    for tname, (rs, ys) in targets.items():
        print(f"\n===== {tname}: n={len(rs)}  classes={dict(collections.Counter(ys).most_common(4))}")
        res = {}
        for ename, folds in (("time", folds_time(rs)), ("task", folds_task(rs))):
            lifts, b, n = evaluate(rs, ys, folds)
            best = max(v for k, v in lifts.items() if k != REFERENCE)
            ge = 0
            for _ in range(perms):
                sh = ys[:]
                rng.shuffle(sh)
                pl, _, _ = evaluate(rs, sh, folds)
                ge += max(v for k, v in pl.items() if k != REFERENCE) >= best
            p = (ge + 1) / (perms + 1)
            res[ename] = (lifts, b, n, p)
            print(f"  [{ename}-ordered] test n={n} baseline acc={b:.3f}  best-lift permutation p={p:.3f}")
            for k, v in sorted(lifts.items(), key=lambda kv: -kv[1]):
                print(f"      {'*' if k in SINGLE else ' '} {k:28s} lift {v:+.3f}")
        bs = {e: max(res[e][0][k] for k in SINGLE) for e in res}
        combos = [k for k in PREDICTORS if k not in SINGLE and k != REFERENCE]
        # where the leave-one-task-out lift of the best single lens comes from
        bl = max(SINGLE, key=lambda k: res["task"][0][k])
        per = collections.Counter()
        for tr, te in folds_task(rs):
            tables, maj = fit([rs[i] for i in tr], [ys[i] for i in tr])
            for i in te:
                per[rs[i]["task"]] += (predict(tables, maj, bl, rs[i]) == ys[i]) - (ys[i] == maj)
        print(f"  share of '{bl}' leave-one-task-out gain by task (correct minus baseline): "
              f"{per.most_common(4)} ... lowest {per.most_common()[-2:]}")
        win = [k for k in combos if all(res[e][0][k] > bs[e] and res[e][0][k] >= 0.05 for e in res)]
        verdicts[tname] = (win, res["task"][3], bs)
        print(f"  combinations beating the best single lens in both, lift >= 0.05: {win or 'none'}; "
              f"leave-one-task-out p={res['task'][3]:.3f}; best single lift time {bs['time']:+.3f} task {bs['task']:+.3f}")
    print("\nDECISION per the rule:")
    for t, (win, p, bs) in verdicts.items():
        if win and p < 0.05:
            print(f"  {t}: combination worth a slice: {win}")
        elif bs["task"] > 0 and p < 0.05:
            print(f"  {t}: no combination; a single lens is the candidate (p={p:.3f})")
        else:
            print(f"  {t}: no predictor (p={p:.3f})")
    if "--rows" in sys.argv:
        for r in rows[:40]:
            print(r["task"], r["cause"], r["course"], r["context"], r["ended"], r["cycles"], r["fix"])


if __name__ == "__main__":
    main()
