#!/usr/bin/env python3
"""Keeps .claude-memory/refactoring_notes.md true to the code: every class a section names (a class heading or a
concern's Classes: line) must point at a file that exists and still declares it, so a rename or deletion cannot
leave an orphaned entry.

Class section:    ## `ClassName` -- `path/to/ClassName.java`
Concern section:  ## Concern: <name>
                  Classes: `A` -- `path/A.java`; `B` -- `path/B.java`
Entry format:     - YYYY-MM-DD -- finding ...

Reported (exit 1): a heading not in either format, a concern without a Classes: line, a path that does not exist,
a file that does not declare the class, a section with no entries (delete it when its last entry is done), an entry
without a date.

usage: refactoring_notes_check.py
"""
import os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
NOTES = os.path.join(ROOT, '.claude-memory', 'refactoring_notes.md')
HEADING = re.compile(r'^## `([A-Za-z_]\w*)` -- `([^`]+)`\s*$')
CONCERN = re.compile(r'^## Concern: \S')
PAIR = re.compile(r'`([A-Za-z_]\w*)` -- `([^`]+)`')
DECL = r'\b(?:class|record|interface|enum)\s+{}\b'
ENTRY = re.compile(r'^- \d{4}-\d{2}-\d{2} -- \S')


def check_class(problems, n, name, path):
    full = os.path.join(ROOT, path)
    if not os.path.isfile(full):
        problems.append(f'line {n}: {name}: no such file {path}')
    elif not re.search(DECL.format(re.escape(name)), open(full, encoding='utf-8').read()):
        problems.append(f'line {n}: {path} no longer declares {name}')


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
            if CONCERN.match(line):
                sections.append([line[3:].strip(), n, 0, 'concern'])
                continue
            m = HEADING.match(line)
            if not m:
                problems.append(f'line {n}: heading is neither "## `Class` -- `path`" nor "## Concern: <name>": {line}')
                sections.append(None)
                continue
            name, path = m.groups()
            check_class(problems, n, name, path)
            sections.append([name, n, 0, 'class'])
        elif line.startswith('Classes:') and sections and sections[-1] is not None and sections[-1][3] == 'concern':
            pairs = PAIR.findall(line)
            if not pairs:
                problems.append(f'line {n}: Classes: line names no `Class` -- `path` pair')
            for name, path in pairs:
                check_class(problems, n, name, path)
            sections[-1][3] = 'concern-with-classes'
        elif line.startswith('- ') and sections and sections[-1] is not None:
            if ENTRY.match(line):
                sections[-1][2] += 1
            else:
                problems.append(f'line {n}: entry without a "YYYY-MM-DD -- " date: {line[:80]}')
    for s in sections:
        if s is not None and s[3] == 'concern':
            problems.append(f'line {s[1]}: {s[0]} has no Classes: line')
        if s is not None and s[2] == 0:
            problems.append(f'line {s[1]}: {s[0]} has no entries -- delete the heading')
    for p in problems:
        print(p)
    if not problems:
        print(f'refactoring notes: {sum(1 for s in sections if s)} section(s), '
              f'{sum(s[2] for s in sections if s)} entr(y/ies), 0 problems')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
