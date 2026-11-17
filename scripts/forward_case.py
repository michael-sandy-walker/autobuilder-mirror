#!/usr/bin/env python3
"""A FORWARD case for T4-EZ's ticket cycle: an OPEN ticket cut from HEAD, in the layout every change target uses
(data/<name>/bootstrap/: seed, protected paths, module/files.txt + pom, withheld/, origin-rev, yardstick), so
scripts/prepare_module_change.sh prepares it and preflight runs it like a ladder case (scripts/ladder_case.py).

The ladder cuts a PAST commit; here there is no commit yet. The cut is the dependency closure (ladder_harvest's)
of the classes the ticket concerns, read at a revision, plus the existing tests that reach nothing outside it.
The seed quotes the ticket text; the specification is always WITHHELD (written before the run, never shown to the
loop; the yardstick runs it); the existing tests are protected, the loop may add its own.

usage: forward_case.py <rev> <name> <ticket-text-file> <withheld-test-file>... --classes <MainPath>[,<MainPath>]
                       [--trim <TestClass>#<method>[,...]] --change <Class>[,<Class>]
  --trim  ship an existing test WITHOUT one method whose reach goes outside the cut (as a protected copy in
          bootstrap/spec); the full test still runs in the full suite when a result is ported back
"""
import argparse
import datetime
import os
import re
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ladder_harvest as h  # noqa: E402
import ladder_case as lc  # noqa: E402
import toycase_version  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')


def drop_method(text, method):
    """The source without one @Test method (its annotations, signature and braces)."""
    m = re.search(r'\n([ \t]*@Test[^\n]*\n(?:[ \t]*@[^\n]*\n)*[ \t]*[^\n]*\b' + re.escape(method) + r'\s*\([^)]*\)[^{]*\{)', text)
    if not m:
        sys.exit(f'--trim: no @Test method {method}')
    i, depth = m.end(1), 1
    while depth:
        depth += {'{': 1, '}': -1}.get(text[i], 0)
        i += 1
    return text[:m.start(1)] + text[i:].lstrip('\n')


def cut(rev, classes, trims=()):
    tree = h.Tree(rev)
    allowed = set()
    light = lambda ext: all(x.startswith(h.LIGHT_LIBRARIES) for x in ext)  # noqa: E731
    for p in classes:
        files, ext = tree.closure([tree.text(p)])
        if not light(ext):
            sys.exit(f'{p}: its closure needs libraries a cut does not carry: {sorted(ext)}')
        allowed |= files
    # the tests of EVERY class in the cut, not only of the named ones: more of the existing behaviour is protected
    names = {p.rsplit('/', 1)[1][:-5] for p in allowed}
    by_class = {}
    for spec in trims:
        cls, method = spec.split('#')
        by_class.setdefault(cls, []).append(method)
    existing, trimmed = [], {}
    for t in tree.paths:
        if not t.startswith(h.TEST):
            continue
        text = tree.text(t)
        cls = t.rsplit('/', 1)[1][:-5]
        if cls in by_class:
            for method in by_class[cls]:
                text = drop_method(text, method)
        if not any(re.search(r'\b' + n + r'\b', text) for n in names):
            continue
        if lc.BOOTS_THE_APPLICATION.search(h.NOISE.sub(' ', text)):
            continue
        unresolved = set()
        files, ext = tree.closure([text], tests=True, unresolved=unresolved)
        main = {f for f in files if f.startswith(h.MAIN)}
        if main <= allowed and light(ext) and not unresolved:
            if cls in by_class:
                trimmed[t] = text
            else:
                existing.append(t)
            existing += [f for f in files if f.startswith(h.TEST) and f != t]   # helpers shipped beside it
        elif cls in by_class:
            sys.exit(f'--trim {cls}: still reaches outside the cut: {sorted(main - allowed)[:3]} {sorted(ext)[:3]}')
    # the resources the shipped tests read (getResourceAsStream("/x")), from the same revision
    resources = set()
    for t in set(existing) | set(trimmed):
        text = trimmed.get(t) or tree.text(t)
        for r in re.findall(r'getResource(?:AsStream)?\(\s*"/([^"]+)"', text):
            path = 'src/test/resources/' + r
            if h.git('ls-tree', '--name-only', rev, path).strip() == path:   # tracked at this revision
                resources.add(path)
    return sorted(allowed), sorted(set(existing) | resources), trimmed


def main():
    a = argparse.ArgumentParser()
    a.add_argument('rev'); a.add_argument('name'); a.add_argument('ticket'); a.add_argument('withheld', nargs='+')
    a.add_argument('--classes', required=True)
    a.add_argument('--trim', default='')
    # the yardstick (smoke_criterion_status.sh) reads the classes to change from the seed and holds every other
    # source of the cut byte for byte
    a.add_argument('--change', required=True)
    o = a.parse_args()
    classes = o.classes.split(',')
    files, existing, trimmed = cut(o.rev, classes, [x for x in o.trim.split(',') if x])
    b = os.path.join(ROOT, 'data', o.name, 'bootstrap')
    if os.path.exists(b):
        sys.exit(f'{b} exists; a toy case is versioned, never overwritten')
    rev = h.git('rev-parse', o.rev).strip()
    os.makedirs(os.path.join(b, 'module'))
    open(os.path.join(b, 'origin-rev'), 'w').write(rev + '\n')
    open(os.path.join(b, 'module', 'files.txt'), 'w').write(
        f"# A forward cut at {rev[:10]} around {', '.join(c.rsplit('/', 1)[1][:-5] for c in classes)}: main sources and\n"
        f"# their existing tests, read from git at origin-rev. Generated by scripts/forward_case.py.\n"
        + '\n'.join(files + existing) + '\n')
    open(os.path.join(b, 'module', 'pom.xml'), 'w').write(lc.module_pom(rev, o.name))
    open(os.path.join(b, 'module', 'README.md'), 'w').write(
        f"# {o.name}\n\nA part of Autobuilder, cut out as it is at {rev[:10]}: "
        + ', '.join(sorted(p.rsplit('/', 1)[1][:-5] for p in files)) + '.\n')
    for t, text in trimmed.items():
        dst = os.path.join(b, 'spec', t)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        open(dst, 'w').write(text)
    for w in o.withheld:
        src = open(w).read()
        pkg = re.search(r'^package\s+([\w.]+);', src, re.M).group(1)
        dst = os.path.join(b, 'withheld', h.TEST, pkg.replace('.', '/'), os.path.basename(w))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy(w, dst)
    ticket = '\n'.join('    ' + l if l.strip() else '' for l in open(o.ticket).read().strip().split('\n'))
    seed = ['  An existing project, adopted, and now asked to change. It was written by someone else and it already',
            '  builds; its existing behaviour is characterised by the tests in src/test/java.',
            '  The change wanted is written in the ticket that asked for it. In the ticket\'s own words:', '', ticket, '',
            ('  The one class to change is ' + o.change.split(',')[0] + '.' if ',' not in o.change else
             '  The classes to change are ' + ', '.join(o.change.split(',')[:-1]) + ' and ' + o.change.split(',')[-1] + '.'),
            '  Write the tests the change needs yourself, beside the existing ones; the operator will check the',
            '  change with tests of their own.', '',
            '  DO NOT modify the existing tests under src/test: they are the operator\'s, and if one of them fails, the',
            '  code is what is wrong. Keep the project\'s packages, classes and naming.']
    open(os.path.join(b, 'seed.txt'), 'w').write('\n'.join(seed) + '\n')
    open(os.path.join(b, 'protected-paths.txt'), 'w').write(
        '# T4-CQ: paths under output/ the loop may not write, one glob per line.\n'
        '# The existing tests are the operator\'s; the loop may ADD tests (the withheld rung).\n'
        + '\n'.join(existing + sorted(trimmed)) + '\n')
    open(os.path.join(b, 'yardstick'), 'w').write(
        '# T4-DG: the yardstick this target is scored by. One line: <script> [mode]\n'
        'scripts/smoke_ladder_case.sh terminating\n')
    open(os.path.join(b, 'VERSION'), 'w').write('1\n')
    open(os.path.join(b, 'CHANGELOG.md'), 'w').write(
        f"# {o.name}\n\n## v1 -- {datetime.date.today().isoformat()}\n\n- reason: generated by scripts/forward_case.py at "
        f"{rev[:10]} (T4-EZ's ticket cycle): {len(files)} main files, {len(existing)} existing tests, "
        f"{len(trimmed)} trimmed ({o.trim or 'none'}), {len(o.withheld)} withheld specification file(s).\n"
        f"- bootstrap digest: `{toycase_version.digest(o.name)}`\n")
    print(f"{o.name}: {len(files)} main files, {len(existing)} existing tests, {len(trimmed)} trimmed, "
          f"{len(o.withheld)} withheld -> {b}")
    for f in files + existing + sorted(trimmed):
        print('   ', f)


if __name__ == '__main__':
    main()
