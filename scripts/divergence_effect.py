#!/usr/bin/env python3
"""Does divergent evolution help? Per cycle: divergent (a 'Recombination produced' line) or plain
('Evolution produced'), the trigger, and the CYCLE line's mut= result. Compared at equal
rejection-streak length on the same subgoal (divergence fires after failures)."""
import re, sys, glob, os, collections, json
CYC = re.compile(r'CycleLoggingService - CYCLE (\S+) goal=\S+ sub=(\S+) prog=(\S+) mut=(\S+) result=(\S+)')
EVO = re.compile(r'EvolutionAgent - (Recombination|Evolution) produced')
TRIG = [('regressing', 'divergence requested due to'), ('stagnation', 'divergent evolution due to STAGNATION')]
rows = []
for log in sys.argv[1:]:
    run = log
    kinds, trig, streak, lastsub = [], set(), 0, None
    with open(log, errors='replace') as f:
        for line in f:
            m = EVO.search(line)
            if m: kinds.append(m.group(1)); continue
            for name, pat in TRIG:
                if pat in line: trig.add(name)
            m = CYC.search(line)
            if m:
                cyc, sub, prog, mut, res = m.groups()
                if sub != lastsub: streak = 0
                if kinds:
                    div = 'Recombination' in kinds
                    t = 'plain' if not div else ('+'.join(sorted(trig)) or 'request')
                    rows.append(dict(run=run, date=os.path.basename(run), streak=streak, div=div, trig=t, mut=mut,
                                     merged=(res == 'MERGED' or 'merged=true' in line)))
                streak = 0 if mut == 'ACCEPTED' else streak + (1 if kinds else 0)
                lastsub, kinds, trig = sub, [], set()
json.dump(rows, open(os.environ.get('OUT', '/dev/null'), 'w'))
def rate(rs): 
    n = len(rs); a = sum(r['mut'] == 'ACCEPTED' for r in rs); return n, a, (100*a/n if n else 0)
print(f"cycles with an evolution: {len(rows)} from {len(sys.argv)-1} logs; mut states: {dict(collections.Counter(r['mut'] for r in rows))}")
print("\nacceptance by rejection streak before the cycle (same subgoal):")
print(f"{'streak':>8} {'plain n':>8} {'acc%':>6} {'diverg n':>9} {'acc%':>6}")
for lo, hi, lab in [(0,0,'0'),(1,1,'1'),(2,2,'2'),(3,4,'3-4'),(5,9,'5-9'),(10,10**6,'10+')]:
    p = [r for r in rows if lo <= r['streak'] <= hi and not r['div']]
    d = [r for r in rows if lo <= r['streak'] <= hi and r['div']]
    print(f"{lab:>8} {rate(p)[0]:8d} {rate(p)[2]:6.1f} {rate(d)[0]:9d} {rate(d)[2]:6.1f}")
print("\ndivergent cycles by trigger (streak>=1):")
for t, rs in sorted(collections.defaultdict(list, {k: [r for r in rows if r['div'] and r['streak']>=1 and r['trig']==k] for k in set(r['trig'] for r in rows)}).items()):
    if t != 'plain': print(f"  {t:24s} n={rate(rs)[0]:4d} acc%={rate(rs)[2]:5.1f}")
