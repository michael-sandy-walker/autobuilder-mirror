#!/usr/bin/env python3
"""T4-EZ: descriptive check (not in the rule) -- do the lenses predict anything WITHIN build failures?"""
import sys,random,collections
sys.argv=['x']; sys.path.insert(0,'/home/anugeshtu/IdeaProjects/autobuilder/scripts')
import t4ez_combination as c
rows=[r for r in c.build_rows() if r['source']=='BUILD']
ended=[r for r in rows if r['ended']]
rng=random.Random(17)
for tname,(rs,ys) in {'T1 ended (BUILD only)':(rows,[r['ended'] for r in rows]),
                      'T3 fix form (BUILD only)':(ended,[r['fix'] for r in ended])}.items():
    print('=====',tname,'n=',len(rs),dict(collections.Counter(ys).most_common(5)))
    for en,folds in (('time',c.folds_time(rs)),('task',c.folds_task(rs))):
        l,b,n=c.evaluate(rs,ys,folds)
        best=max(v for k,v in l.items() if k!=c.REFERENCE); ge=0
        for _ in range(200):
            sh=ys[:]; rng.shuffle(sh); pl,_,_=c.evaluate(rs,sh,folds); ge+=max(v for k,v in pl.items() if k!=c.REFERENCE)>=best
        print(f'  [{en}] n={n} baseline={b:.3f} p={(ge+1)/201:.3f}  '+'  '.join(f'{k.split(" (")[0]} {v:+.3f}' for k,v in sorted(l.items(),key=lambda kv:-kv[1])))
