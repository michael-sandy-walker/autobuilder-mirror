#!/usr/bin/env python3
"""T4-EZ lenses 1 + 2: a stuck problem's CAUSE (from its own answers) and the FORM of the change that ended it.

Rule on the T4-EZ ticket (2026-11-17), written before counting. Reads the run archive:
  stuck-ledger.json      entries, their answers, endedCycle
  autobuilder.log.gz     every model response in full; `Wrote to mutations: mutations/<cycle>/<path>` ties a
                         response's code block to the cycle that applied it
  artifact-src.tar.gz    the run's source listing (is a type the project's own?)

Usage: python3 scripts/t4ez_cause_fix.py [--table] [--control-only] [--dump N]
"""
import collections
import difflib
import gzip
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
import t4ez_shape_recurrence as shp  # noqa: E402

ARCHIVE = shp.ARCHIVE
TS = re.compile(r"^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d")
WROTE = re.compile(r"Wrote to mutations: mutations/(cycle-[\w-]+?)/(.+)$")
FENCE = re.compile(r"^```(\w+)\s*\[([^\]]+)\]\s*$")
SYNTAX = ("class, interface, enum, or record expected", "unnamed class", "reached end of file",
          "unclosed string literal", "illegal start of", "<identifier> expected", "';' expected")


# ---------------- the run's log: which code each cycle applied ----------------

def parse_fence(kind, body):
    """-> (full_text or None, removed_lines, added_lines)"""
    text = "\n".join(body)
    if kind == "edit" or "<<<<<<< SEARCH" in text:
        removed, added = [], []
        for m in re.finditer(r"<<<<<<< SEARCH\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE", text, re.S):
            removed += m.group(1).split("\n")
            added += m.group(2).split("\n")
        return None, removed, added, [(m.group(1), m.group(2)) for m in
                                      re.finditer(r"<<<<<<< SEARCH\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE", text, re.S)]
    return text, [], body, None


def writes_of(run_dir):
    """cycle -> [(path, whole_file_written, removed, added)]"""
    path = os.path.join(run_dir, "autobuilder.log.gz")
    if not os.path.exists(path):
        return None
    latest = {}          # path -> (full, removed, added, hunks)
    content = {}         # path -> last known full content
    out = collections.defaultdict(list)
    in_resp = False
    fence = None
    body = []
    with gzip.open(path, "rt", errors="replace") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if TS.match(line):
                in_resp = "[LLM RESPONSE]" in line
                fence = None
                w = WROTE.search(line)
                if w:
                    cyc, p = w.group(1), w.group(2).strip()
                    got = latest.get(p)
                    if got is None:
                        out[cyc].append((p, None, [], []))
                        continue
                    full, removed, added, hunks = got
                    if full is not None:
                        prev = content.get(p)
                        if prev is not None:
                            d = list(difflib.unified_diff(prev.split("\n"), full.split("\n"), lineterm="", n=0))
                            removed = [x[1:] for x in d if x.startswith("-") and not x.startswith("---")]
                            added = [x[1:] for x in d if x.startswith("+") and not x.startswith("+++")]
                            out[cyc].append((p, False, removed, added))
                        else:
                            out[cyc].append((p, True, [], full.split("\n")))
                        content[p] = full
                    else:
                        out[cyc].append((p, False, removed, added))
                        if p in content and hunks:
                            c = content[p]
                            for s, r in hunks:
                                c = c.replace(s, r, 1)
                            content[p] = c
                continue
            if not in_resp:
                continue
            if fence is None:
                m = FENCE.match(line.strip())
                if m:
                    fence = (m.group(1), m.group(2).strip())
                    body = []
            else:
                if line.strip() == "```":
                    latest[fence[1]] = parse_fence(fence[0], body)
                    fence = None
                else:
                    body.append(line)
    return out


# ---------------- lens 1: cause ----------------

def own_types(run_dir):
    names = set()
    for tarball in ("artifact-src.tar.gz", "preserved-artifact-src.tar.gz"):
        p = os.path.join(run_dir, tarball)
        if os.path.exists(p):
            try:
                ls = subprocess.run(["tar", "-tzf", p], capture_output=True, text=True, timeout=60).stdout
                names |= {os.path.basename(x)[:-5] for x in ls.split("\n") if x.endswith(".java")}
            except Exception:
                pass
    return names


def named(e):
    """The symbol, member or type the signature names (for lens 2), and the type that lacks it."""
    sig = e["signature"]
    det = " ".join(e.get("detail") or [])
    m = re.search(r"(\w+) has no (\w+)\(", sig)
    if m:
        return m.group(2), m.group(1)
    m = re.search(r"cannot find symbol (?:class|variable|method) (\w+)", sig) or \
        re.search(r"symbol:\s+(?:class|variable|method) (\w+)", det)
    if m:
        return m.group(1), None
    m = re.search(r"'(\w+)' already exists", sig) or re.search(r"method (\w+)\(.*already defined", sig) or \
        re.search(r"constructor (\w+) in", sig)
    if m:
        return m.group(1), None
    return None, None


def cause(e, owns):
    src = e["source"]
    answers = " ".join((q.get("answer") or "") for q in e.get("questions") or [])
    if src == "BUILD":
        s = shp.shape(e)
        if re.search(r"No class named \w+ exists", answers):
            return "type invented"
        if "declares no class" in answers and "it is declared as" in answers:
            return "type in another package"
        if re.search(r"'\w+' is declared as: .*\(JDK class\)", answers) or \
                re.search(r"'\w+' is declared as: [^;]+;", answers):
            return "name resolves to another type"
        if "is constructed only as" in answers or "constructor cannot be applied" in s:
            return "constructor shape not offered"
        if "has no <member>" in s or re.search(r"declares: .*It has no", answers):
            name, owner = named(e)
            if owner is None:
                m = re.search(r"(\w+) declares: ", answers)
                owner = m.group(1) if m else None
            return "member invented on own type" if owner in owns else "member invented on external type"
        if any(x in s for x in SYNTAX):
            return "broken file structure"
        if "already defined" in s or "already exists" in s:
            return "duplicate declaration"
        if "incompatible types" in s:
            return "type mismatch"
        if "lambda expression must be final" in s:
            return "captured variable reassigned"
        if "cannot find symbol variable" in s:
            return "undeclared variable"
        if "cannot find symbol class" in s:
            return "type not found (no answer)"
        if "cannot find symbol method" in s:
            return "member invented (no answer)"
        return "build: other"
    if src == "TEST_FAILURES":
        m = re.search(r"It failed with: (.*)", answers, re.S)
        msg = m.group(1)[:600] if m else " ".join(e.get("detail") or [])[:600]
        if re.search(r"Argument\(s\) are different|Wanted but not invoked|never used", msg):
            return "interaction mismatch"
        if re.search(r"but was: ?(null|\[\]|\"\"|empty)|but was empty|Expecting empty but|it was empty|"
                     r"Expecting actual not to be null|to contain: .* but was empty|Optional to contain.*but was empty",
                     msg):
            return "nothing returned"
        if re.search(r"Expecting no elements of|not to contain|unexpected", msg):
            return "an extra element"
        if re.search(r"Exception|exception", msg) and "Expecting" not in msg:
            return "an exception"
        if re.search(r"expected|Expecting|Expected", msg):
            return "a wrong value"
        return "test: other"
    if src in ("CLOSURE_REFUSAL", "BOOT"):
        return shp.shape(e).replace("REFUSAL: ", "refusal: ")
    return None


# ---------------- lens 2: fix form ----------------

def declares(lines, name):
    pat = re.compile(r"^\s*(?:@\w+\s+)*(?:(?:public|private|protected|static|final|abstract|default|synchronized)\s+)*"
                     r"(?:(?:class|record|interface|enum)\s+" + re.escape(name) + r"\b|"
                     r"[\w<>\[\],.? ]+\s+" + re.escape(name) + r"\s*(?:\(|=|;))")
    return any(pat.match(x) and not x.strip().startswith(("return", "//", "*")) for x in lines)


def fix_form(e, writes):
    if writes is None:
        return "no log"
    m = re.match(r"cycle-\d+", e.get("endedCycle") or "")
    ws = writes.get(m.group(0) if m else e.get("endedCycle")) or []  # a test gate's cycle id carries a suffix
    if not ws:
        return "no write in the ending cycle"
    src = e["source"]
    if src == "BUILD":
        name, _ = named(e)
        if name:
            word = re.compile(r"\b" + re.escape(name) + r"\b")
            if any(declares(a, name) for _, _, _, a in ws):
                return "name declared"
            if any(any(word.search(x) for x in r) and not any(word.search(x) for x in a) for _, whole, r, a in ws
                   if not whole):
                return "reference replaced"
            sig_file = re.search(r"(\w+)\.java:", e["signature"])
            if any(whole and not any(word.search(x) for x in a) and (sig_file is None or
                                                                    os.path.basename(p) == sig_file.group(1) + ".java")
                   for p, whole, _, a in ws):
                return "reference replaced"
        if any(any(x.strip().startswith("import ") for x in r + a) and
               sorted(x for x in r if x.strip().startswith("import ")) != sorted(x for x in a if x.strip().startswith("import "))
               for _, _, r, a in ws):
            return "import changed"
        if any(whole for _, whole, _, _ in ws):
            return "file written whole"
        return "other edit"
    paths = [p for p, _, _, _ in ws]
    test = [p for p in paths if "/src/test/" in "/" + p]
    conf = [p for p in paths if not p.endswith(".java")]
    main = [p for p in paths if p not in test and p not in conf]
    if conf and not test and not main:
        return "build configuration"
    if test and main:
        return "test and code"
    if test:
        return "test only"
    return "code only"


# ---------------- counting ----------------

def majority(c):
    top, n = c.most_common(1)[0]
    return top, n * 2 > sum(c.values())


def control():
    run = os.path.join(ARCHIVE, "2026-10-11_07-34-41")
    import json
    entries = json.load(open(os.path.join(run, "stuck-ledger.json")))
    e = next(x for x in entries if "getComponents()" in x["signature"] and x["status"] == "ENDED")
    got = fix_form(e, writes_of(run))
    print(f"positive control: '{e['signature']}' -> {got}")
    return got == "reference replaced"


def main():
    if not control():
        print("CONTROL FAILED -- fix the extractor before counting")
        sys.exit(1)
    if "--control-only" in sys.argv:
        return
    rows = []  # (run, task, family, source, cause, shape, fix)
    for run, target, entries in shp.load():
        ended = [e for e in entries if e.get("status") == "ENDED" and e["source"] not in shp.CONTENTLESS
                 and e["source"] != "VETO"]
        if not ended:
            continue
        d = os.path.join(ARCHIVE, run)
        owns = own_types(d)
        writes = writes_of(d)
        task = shp.task_of(target)
        for e in ended:
            rows.append((run, task, shp.family_of(task), e["source"], cause(e, owns), shp.shape(e), fix_form(e, writes), e))

    print(f"ENDED contentful entries: {len(rows)}")
    print("fix forms:", collections.Counter(r[6] for r in rows).most_common())
    usable = [r for r in rows if r[6] not in ("no log",)]

    # (1) cause recurrence (descriptive)
    tasks_of = collections.defaultdict(set)
    fams_of = collections.defaultdict(set)
    for r in usable:
        tasks_of[r[4]].add(r[1])
        fams_of[r[4]].add(r[2])

    # (2) Cw and Cx
    by_tc = collections.defaultdict(collections.Counter)
    for r in usable:
        by_tc[(r[1], r[4])][r[6]] += 1
    pairs = [c for c in by_tc.values() if sum(c.values()) >= 2]
    cw = sum(1 for c in pairs if majority(c)[1]) / len(pairs) if pairs else float("nan")

    def cx(by_family):
        groups = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
        for r in usable:
            groups[r[4]][r[2] if by_family else r[1]][r[6]] += 1
        multi, same, detail = 0, 0, []
        for cz, per in groups.items():
            if len(per) < 2:
                continue
            multi += 1
            modal = collections.Counter(c.most_common(1)[0][0] for c in per.values())
            top, ok = majority(modal)
            same += ok
            detail.append((cz, len(per), top, ok, dict(modal)))
        return same, multi, detail

    RESIDUAL = ("other edit", "no write in the ending cycle")

    s, m, det = cx(False)
    sf, mf, detf = cx(True)
    print(f"\nCw (task, cause) ENDED >= 2: one fix form holds a majority in {sum(1 for c in pairs if majority(c)[1])}/{len(pairs)} = {cw:.2f}")
    print(f"Cx tasks:    {s}/{m} causes ENDED in >=2 tasks share a majority fix form = {s / m if m else float('nan'):.2f}"
          f"   (bar 0.75 x Cw = {0.75 * cw:.2f})")
    print(f"   of the {s} agreements, on a residual form (other edit / no write): "
          f"{sum(1 for d in det if d[3] and d[2] in RESIDUAL)}")
    print(f"Cx families: {sf}/{mf}" + ("   (under 5: not measurable yet)" if mf < 5 else ""))
    for cz, n, top, ok, modal in sorted(det, key=lambda x: -x[1]):
        print(f"   {'=' if ok else 'x'} {cz:40s} tasks={n:2d} majority={top:28s} {modal}")

    # (3) purity: fix-form majority share given cause vs given shape
    def purity(key):
        g = collections.defaultdict(collections.Counter)
        for r in usable:
            g[r[key]][r[6]] += 1
        return sum(c.most_common(1)[0][1] for c in g.values()) / len(usable), len(g)
    pc, nc = purity(4)
    ps, ns = purity(5)
    print(f"\npurity of the fix form given the CAUSE: {pc:.2f} over {nc} causes; given the SHAPE: {ps:.2f} over {ns} shapes")

    print("\ncause recurrence (descriptive): cause -> tasks / families")
    for cz in sorted(tasks_of, key=lambda k: -len(tasks_of[k])):
        print(f"   {cz:45s} {len(tasks_of[cz]):2d} {len(fams_of[cz]):2d}  {sorted(fams_of[cz]) if len(fams_of[cz]) > 1 else ''}")

    if "--dump" in sys.argv:
        n = int(sys.argv[sys.argv.index("--dump") + 1])
        import random
        random.seed(7)
        for r in random.sample(usable, min(n, len(usable))):
            print(f"\n-- {r[0]} {r[1]} | {r[4]} | {r[6]}\n   {r[7]['signature'][:160]}")


if __name__ == "__main__":
    main()
