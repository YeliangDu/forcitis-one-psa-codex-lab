import sys, json; sys.path.insert(0,'.')
from jser import *
import os, sys
BASE = (sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))) + '/'
def colname(c):
    s=''
    c+=1
    while c: c,r=divmod(c-1,26); s=chr(65+r)+s
    return s
def pos(o): return (o.f['com.ufsoft.table.CellPosition.row'],o.f['com.ufsoft.table.CellPosition.col'])
def load(name):
    P=Parser(open(BASE+name+'.bin','rb').read()); root=P.parse()[0]
    cells={}
    for x in P.h:
        if isinstance(x,Obj) and x.cd.name=='com.ufsoft.table.Cell':
            v=x.f['com.ufsoft.table.Cell.m_Value']
            if v is not None and str(v)!='':
                cells[(x.f['com.ufsoft.table.Cell.m_nRow'],x.f['com.ufsoft.table.Cell.m_nCol'])]=str(v)
    fm=[x for x in P.h if isinstance(x,Obj) and x.cd.name.endswith('.FormulaModel')][0]
    forms=[]  # (row,col,rowEnd,colEnd,content,id)
    other=[]
    def walk_ht(ht):
        a=ht.ann[0][1]
        items=[x for x in a if not (isinstance(x,tuple))]
        for i in range(0,len(items),2):
            k,v=items[i],items[i+1]
            yield k,v
    for fname in ('public_instantFormula','personal_instantFormula','totalFormula'):
        ht=fm.f['com.ufsoft.iufo.fmtplugin.formula.FormulaModel.'+fname]
        for unit,inner in walk_ht(ht):
            for k,v in walk_ht(inner):
                if k.cd.name.endswith('CellPosition'):
                    r,c=pos(k); r2,c2=r,c
                else:
                    a=k.f['com.ufsoft.table.AreaPosition.startCell'];b=k.f['com.ufsoft.table.AreaPosition.endCell']
                    r,c=pos(a); r2,c2=pos(b)
                if isinstance(v,Obj) and v.cd.name.endswith('FormulaVO'):
                    forms.append(dict(src=fname,row=r,col=c,row2=r2,col2=c2,content=v.f['com.ufsoft.iufo.fmtplugin.formula.FormulaVO.formulaContent'],id=v.f['com.ufsoft.iufo.fmtplugin.formula.FormulaVO.id']))
                else: other.append((fname,r,c,repr(v)[:100]))
    checks=[]
    for x in P.h:
        if isinstance(x,Obj) and x.cd.name.endswith('SimpleCheckFmlVO'):
            checks.append({k.split('.')[-1]:v for k,v in x.f.items()})
    return P,root,cells,forms,other,checks
if __name__=='__main__':
    for n in ['QZ_rep_01','QZ_rep_02','QZ_rep_03','SF_rep_03']:
        P,root,cells,forms,other,checks=load(n)
        print(n,'cells',len(cells),'forms',len(forms),'other',other[:3],'checks',len(checks), 'src',{f['src'] for f in forms})
        print(' areaforms',sum(1 for f in forms if (f['row'],f['col'])!=(f['row2'],f['col2'])))
        print(' dup keys',len(forms)-len({(f['row'],f['col'],f['row2'],f['col2']) for f in forms}))
        print(' maxrow',max(r for r,c in cells),'maxcol',max(c for r,c in cells))
