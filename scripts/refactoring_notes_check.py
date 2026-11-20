#!/usr/bin/env python3
"""Keeps .claude-memory/refactoring_notes.md true to the code: every class heading must point at a file that
exists and still declares that class, so a rename or deletion cannot leave an orphaned entry.

Heading format:   ## `ClassName` -- `path/to/ClassName.java`
Entry format:     - YYYY-MM-DD -- finding ...

Reported (exit 1): a heading not in that format, a path that does not exist, a file that does not declare the
class, a heading with no entries (delete the heading when its last entry is done), an entry without a date.

usage: refactoring_notes_check.py
"""
import os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
NOTES = os.path.join(ROOT, '.claude-memory', 'refactoring_notes.md')
HEADING = re.compile(r'^## `([A-Za-z_]\w*)` -- `([^`]+)`\s*$')
DECL = r'\b(?:class|record|interface|enum)\s+{}\b'
ENTRY = re.compile(r'^- \d{4}-\d{2}-\d{2} -- \S')


def main():
    if not os.path.exists(NOTES):
        print('no refactoring notes file -- nothing to check')
        return 0
    problems = []
    lines = open(NOTES, encoding='utf-8').read().split('\n')
    sections = []
    in_body = False
    for n, line in enumerate(lines, 1):
        if line.strip() == '---':
            in_body = True
            continue
        if not in_body:
            continue
        if line.startswith('## '):
            m = HEADING.match(line)
            if not m:
                problems.append(f'line {n}: heading is not "## `Class` -- `path`": {line}')
                sections.append(None)
                continue
            name, path = m.groups()
            full = os.path.join(ROOT, path)
            if not os.path.isfile(full):
                problems.append(f'line {n}: {name}: no such file {path}')
            elif not re.search(DECL.format(re.escape(name)), open(full, encoding='utf-8').read()):
                problems.append(f'line {n}: {path} no longer declares {name}')
            sections.append([name, n, 0])
        elif line.startswith('- ') and sections and sections[-1] is not None:
            if ENTRY.match(line):
                sections[-1][2] += 1
            else:
                problems.append(f'line {n}: entry without a "YYYY-MM-DD -- " date: {line[:80]}')
    for s in sections:
        if s is not None and s[2] == 0:
            problems.append(f'line {s[1]}: {s[0]} has no entries -- delete the heading')
    for p in problems:
        print(p)
    if not problems:
        print(f'refactoring notes: {sum(1 for s in sections if s)} class(es), '
              f'{sum(s[2] for s in sections if s)} entr(y/ies), 0 problems')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
