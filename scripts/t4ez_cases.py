#!/usr/bin/env python3
"""T4-EZ: cases -- situation -> action -> result -- across the archived runs (the user's framing, 2026-11-17).

Rule on the T4-EZ ticket (2026-11-17), written before counting. A situation is a rejected mutation, a closure
refusal or a discarded response; the action is what the loop did next for that subgoal; R1 = the subgoal was
COMPLETED by the run's end, R2 = the same situation came back on it within 3 cycles.

Usage: python3 scripts/t4ez_cases.py [--perms N] [--show RUN] [--keys]
"""
import collections
import glob
import gzip
import json
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
import t4ez_shape_recurrence as shp  # noqa: E402

ARCHIVE = shp.ARCHIVE


def base(cycle):
    m = re.match(r"cycle-\d+", cycle or "")
    return m.group(0) if m else None


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


def is_test(p):
    return "/src/test/" in "/" + p


def norm_reason(s):
    s = re.sub(r"`[^`]*`|'[^']*'|\"[^\"]*\"", "Q", s or "")
    s = re.sub(r"\d+", "N", s)
    return s[:70]


MET = re.compile(r"fulfilled|verified|banked|Build fixed|observed the claim|satisf|confirmed all", re.I)
REPLACED = re.compile(r"superseded|consolidated|re-scoped|divided|withdrawn", re.I)
replaced_count = [0]


def outcome(last):
    """R1 from the REASON of the subgoal's last transition (the rule's amendment 4): True met, False failed."""
    if last is None:
        return False          # still active at the run's end
    status, reason = last
    if MET.search(reason):
        return True
    if REPLACED.search(reason):
        return "replaced"
    if not reason.strip():
        return status == "COMPLETED"
    return False


def cases_of(run_dir, task):
    evs = list(events(os.path.join(run_dir, "behaviour.ndjson.gz")))
    order = []                 # base cycles in order
    sub = {}                   # base cycle -> subgoal
    for e in evs:
        if e.get("event") == "cycle_start":
            b = base(e.get("cycleId"))
            if b and b not in sub:
                order.append(b)
                pl = e.get("payload") or {}
                sub[b] = pl.get("currentSubGoalId") or pl.get("currentGoalId")   # the SUBGOAL; the root only when none
    pos = {b: i for i, b in enumerate(order)}
    final = {}   # goal -> (status, reason) of its last transition
    for e in evs:
        if e.get("event") == "goal_status_changed":
            p = e.get("payload") or {}
            final[p.get("goalId")] = (p.get("to"), p.get("reason") or "")
    # per-mutation facts
    files, heur, scored, probe = {}, collections.defaultdict(set), {}, {}
    for e in evs:
        p = e.get("payload") or {}
        k = e.get("event")
        if k == "mutation_proposed":
            files[p.get("mutationId")] = (base(e.get("cycleId")), tuple(sorted(p.get("files") or [])))
        elif k == "heuristic_result" and p.get("state") == "REJECTED":
            heur[p.get("mutationId")].add(p.get("heuristic"))
        elif k == "mutation_scored":
            scored[p.get("mutationId")] = p.get("llmState")
        elif k == "mutation_rejected":
            probe[p.get("mutationId")] = p.get("probeResult")
    # event order matters: a plan or halt counts only between the situation and the next proposal
    proposals = []      # (event index, base cycle, files)
    plans, halts = [], []
    for idx, e in enumerate(evs):
        k = e.get("event")
        if k == "mutation_proposed":
            proposals.append((idx, base(e.get("cycleId")), tuple(sorted((e.get("payload") or {}).get("files") or []))))
        elif k == "planning_run":
            plans.append(idx)
        elif k == "rewind_performed" or (k == "system_paused"
                                          and (e.get("payload") or {}).get("reason") != "goal-fulfilled"):
            halts.append(idx)   # the run's own end (goal fulfilled) is not an action
    transitions = collections.defaultdict(list)   # goal -> [event index of each status change]
    for idx, e in enumerate(evs):
        if e.get("event") == "goal_status_changed":
            transitions[(e.get("payload") or {}).get("goalId")].append(idx)
    cycle_start_idx = {}
    for idx, e in enumerate(evs):
        if e.get("event") == "cycle_start":
            cycle_start_idx.setdefault(base(e.get("cycleId")), idx)

    situations = []   # (event index, cycle, goal, key, reference files)
    for idx, e in enumerate(evs):
        p = e.get("payload") or {}
        k = e.get("event")
        b = base(e.get("cycleId"))
        if b not in pos:
            continue
        if k == "mutation_negotiated" and p.get("finalState") == "REJECTED":
            mid = p.get("mutationId")
            key = ("rejected", probe.get(mid) or "-", tuple(sorted(heur.get(mid, ()))),
                   "overridden" if scored.get(mid) and scored.get(mid) != "REJECTED" else "model agreed")
            situations.append((idx, b, sub[b], key, files.get(mid, (None, ()))[1]))
        elif k == "closure_refused":
            situations.append((idx, b, sub[b], ("closure refused", p.get("gate")), None))
        elif k == "llm_response_discarded":
            situations.append((idx, b, sub[b], ("response discarded", p.get("role"), norm_reason(p.get("reason"))), None))

    out = []
    for sidx, b, g, key, ref in situations:
        i = pos[b]
        if ref is None:   # the last proposal on this subgoal before the situation
            ref = ()
            for pidx, pc, fs in reversed(proposals):
                if pidx < sidx and pc in sub and sub[pc] == g:
                    ref = fs
                    break
        if i + 1 >= len(order):
            continue
        nxt = next(((pidx, pc, fs) for pidx, pc, fs in proposals if pidx > sidx and pc in pos and pos[pc] > i), None)
        # the first cycle after the situation that works another subgoal, if any, before the next proposal
        moved_idx = None
        for j in range(i + 1, len(order)):
            if sub[order[j]] != g:
                moved_idx = cycle_start_idx.get(order[j])
                break
        stop = nxt[0] if nxt and (moved_idx is None or nxt[0] < moved_idx) else moved_idx
        if stop is None:
            continue
        if any(sidx < h < stop for h in halts):
            action = "rewind or pause"
        elif moved_idx is not None and stop == moved_idx:
            # descriptive split (after the first count): a subgoal closed before the move is a RESULT, not a choice
            action = "moved on (subgoal already closed)" if any(sidx < t < stop for t in transitions.get(g, ())) \
                else "moved on"
        elif any(sidx < q < stop for q in plans):
            action = "replanned"
        elif any(map(is_test, nxt[2])) != any(map(is_test, ref)):
            action = "switched test/code"
        elif set(nxt[2]) == set(ref):
            action = "retried the same files"
        else:
            action = "changed other files"
        r1 = outcome(final.get(g))
        if r1 == "replaced":
            replaced_count[0] += 1
            continue
        r2 = False
        for j in range(i + 1, min(i + 4, len(order))):
            c = order[j]
            if sub[c] == g and any(s[1] == c and s[3] == key for s in situations):
                r2 = True
                break
        out.append({"task": task, "family": shp.family_of(task), "run": os.path.basename(run_dir), "cycle": b,
                    "goal": g, "key": key, "action": action, "r1": r1, "r2": r2})
    return out


def load_cases():
    cases = []
    for d in sorted(glob.glob(os.path.join(ARCHIVE, "*"))):
        if not os.path.exists(os.path.join(d, "behaviour.ndjson.gz")):
            continue
        target = "?"
        try:
            for line in open(os.path.join(d, "target.txt")):
                if line.startswith("target="):
                    target = line.split("=", 1)[1].strip()
        except OSError:
            pass
        try:
            cases += cases_of(d, shp.task_of(target))
        except Exception as ex:  # a truncated log: say so, do not hide it
            print(f"skipped {os.path.basename(d)}: {ex}", file=sys.stderr)
    return cases


# ---------------- questions ----------------

def rate(cs):
    return sum(c["r1"] for c in cs) / len(cs) if cs else float("nan")


def q2(cases, rng, perms):
    by = collections.defaultdict(list)
    for c in cases:
        by[c["key"]].append(c)
    rows = []
    for key, cs in by.items():
        if len(cs) < 20:
            continue
        acts = collections.Counter(c["action"] for c in cs)
        ok = [a for a, n in acts.items() if n >= 5]
        if len(ok) < 2:
            continue

        def gap(labels):
            g = collections.defaultdict(list)
            for c, a in zip(cs, labels):
                if a in ok:
                    g[a].append(c["r1"])
            rs = {a: sum(v) / len(v) for a, v in g.items()}
            return max(rs.values()) - min(rs.values()), rs
        labels = [c["action"] for c in cs]
        obs, rs = gap(labels)
        ge = 0
        for _ in range(perms):
            sh = labels[:]
            rng.shuffle(sh)
            ge += gap(sh)[0] >= obs
        rows.append((key, len(cs), obs, (ge + 1) / (perms + 1), rs, len({c["task"] for c in cs})))
    return rows


def q3(cases, keys):
    """situation-aware gap minus situation-blind gap, leave-one-task-out. keys: the key of each case."""
    tot_k = collections.defaultdict(lambda: [0, 0])
    tot_a = collections.defaultdict(lambda: [0, 0])
    per_k = collections.defaultdict(lambda: collections.defaultdict(lambda: [0, 0]))
    per_a = collections.defaultdict(lambda: collections.defaultdict(lambda: [0, 0]))
    for c, k in zip(cases, keys):
        for tot, per, kk in ((tot_k, per_k, (k, c["action"])), (tot_a, per_a, c["action"])):
            tot[kk][0] += c["r1"]
            tot[kk][1] += 1
            per[c["task"]][kk][0] += c["r1"]
            per[c["task"]][kk][1] += 1
    best_k_cache, best_a_cache = {}, {}

    def best(task, k):
        ck = (task, k)
        if ck not in best_k_cache:
            cand = []
            for (kk, a), (s, n) in tot_k.items():
                if kk != k:
                    continue
                s2, n2 = s - per_k[task][(kk, a)][0], n - per_k[task][(kk, a)][1]
                if n2 >= 5:
                    cand.append((s2 / n2, a))
            best_k_cache[ck] = max(cand)[1] if cand else None
        return best_k_cache[ck]

    def blind(task):
        if task not in best_a_cache:
            cand = []
            for a, (s, n) in tot_a.items():
                s2, n2 = s - per_a[task][a][0], n - per_a[task][a][1]
                if n2 >= 5:
                    cand.append((s2 / n2, a))
            best_a_cache[task] = max(cand)[1] if cand else None
        return best_a_cache[task]
    m_aw, u_aw, m_bl, u_bl = [], [], [], []
    for c, k in zip(cases, keys):
        rec = best(c["task"], k)
        if rec is None:
            continue
        (m_aw if c["action"] == rec else u_aw).append(c["r1"])
        rb = blind(c["task"])
        (m_bl if c["action"] == rb else u_bl).append(c["r1"])
    f = lambda xs: sum(xs) / len(xs) if xs else float("nan")  # noqa: E731
    aware = f(m_aw) - f(u_aw)
    blindg = f(m_bl) - f(u_bl)
    return aware, blindg, len(m_aw) + len(u_aw), (f(m_aw), len(m_aw), f(u_aw), len(u_aw)), (f(m_bl), len(m_bl), f(u_bl), len(u_bl))


def main():
    perms = int(sys.argv[sys.argv.index("--perms") + 1]) if "--perms" in sys.argv else 200
    if "--show" in sys.argv:
        run = sys.argv[sys.argv.index("--show") + 1]
        d = os.path.join(ARCHIVE, run)
        for c in cases_of(d, "?"):
            print(c["cycle"], c["goal"][:13] if c["goal"] else None, c["key"], "->", c["action"], "| R1", c["r1"], "R2", c["r2"])
        return
    cases = load_cases()
    if "--split-moved" in sys.argv:
        print("closed-before-move cases:", sum(1 for c in cases if c["action"].startswith("moved on (")),
              "R1 among them:", round(rate([c for c in cases if c["action"].startswith("moved on (")]), 3))
        cases = [c for c in cases if not c["action"].startswith("moved on (")]
        print("DESCRIPTIVE RECOUNT without them (not the rule's count)")
    rng = random.Random(23)
    print(f"cases {len(cases)} in {len({c['run'] for c in cases})} runs, {len({c['task'] for c in cases})} tasks, "
          f"{len({c['key'] for c in cases})} situation keys")
    print("by kind:", collections.Counter(c["key"][0] for c in cases).most_common())
    print("actions:", collections.Counter(c["action"] for c in cases).most_common())
    print(f"cases left out because their subgoal was REPLACED: {replaced_count[0]}")
    print(f"R1 overall {rate(cases):.3f}; R2 overall {sum(c['r2'] for c in cases) / len(cases):.3f}")

    # Q1
    tk = collections.defaultdict(set)
    fk = collections.defaultdict(set)
    for c in cases:
        tk[c["key"]].add(c["task"])
        fk[c["key"]].add(c["family"])
    print(f"\nQ1 keys in >= 2 tasks: {sum(1 for v in tk.values() if len(v) >= 2)} of {len(tk)}; "
          f">= 2 families: {sum(1 for v in fk.values() if len(v) >= 2)}; "
          f"cases whose key is in >= 2 tasks: {sum(1 for c in cases if len(tk[c['key']]) >= 2)} of {len(cases)}")

    # Q2
    rows = q2(cases, rng, perms)
    held = [r for r in rows if r[2] >= 0.15 and r[3] < 0.05]
    print(f"\nQ2 keys with >= 20 cases and >= 2 actions of >= 5: {len(rows)}; gap >= 0.15 with p < 0.05: {len(held)}")
    for key, n, g, p, rs, nt in sorted(rows, key=lambda r: -r[1]):
        print(f"   {'*' if (g >= 0.15 and p < 0.05) else ' '} n={n:4d} tasks={nt:2d} gap={g:.2f} p={p:.3f} {str(key)[:95]}")
        print("        " + "; ".join(f"{a} {v:.2f}" for a, v in sorted(rs.items(), key=lambda kv: -kv[1])))

    # Q3
    keys = [c["key"] for c in cases]
    aware, blindg, n, ma, mb = q3(cases, keys)
    diff = aware - blindg
    ge = 0
    for _ in range(perms):
        sh = keys[:]
        rng.shuffle(sh)
        a2, b2, _, _, _ = q3(cases, sh)
        ge += (a2 - b2) >= diff
    p3 = (ge + 1) / (perms + 1)
    print(f"\nQ3 leave-one-task-out over {n} cases with a recommendation:")
    print(f"   situation-aware: R1 {ma[0]:.3f} when the action matched (n={ma[1]}), {ma[2]:.3f} when not (n={ma[3]}) -> gap {aware:+.3f}")
    print(f"   situation-blind: R1 {mb[0]:.3f} when the action matched (n={mb[1]}), {mb[2]:.3f} when not (n={mb[3]}) -> gap {blindg:+.3f}")
    print(f"   aware minus blind {diff:+.3f}, permutation p={p3:.3f}")

    q2ok = len(held) >= 3
    q3ok = diff >= 0.10 and p3 < 0.05
    print("\nDECISION per the rule:",
          "a case memory is worth a slice" if (q2ok and q3ok) else
          ("actions matter, but what worked does not carry across projects" if q2ok else
           "actions do not measurably matter within situations (Q2 fails)"))

    if "--keys" in sys.argv:
        for k, v in sorted(tk.items(), key=lambda kv: -len(kv[1]))[:40]:
            print(len(v), sum(1 for c in cases if c["key"] == k), k)


if __name__ == "__main__":
    main()
