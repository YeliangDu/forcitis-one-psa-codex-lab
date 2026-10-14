import sys,re; sys.path.insert(0,'.')
from extract import *
OUT=BASE+'report_formulas.md'
TITLES={'QZ_rep_01':'权责账套 会企01表 资产负债表（QZ_rep_01.bin）','QZ_rep_02':'权责账套 会企02表 利润表（QZ_rep_02.bin）',
 'QZ_rep_03':'权责账套 会企03表 现金流量表（QZ_rep_03.bin）','SF_rep_03':'收付账套 K会03 现金流量表（SF_rep_03.bin）'}
def measure_map(P):
    mm=[x for x in P.h if isinstance(x,Obj) and x.cd.name.endswith('MeasureModel')][0]
    allm=mm.f['com.ufsoft.iufo.fmtplugin.measure.MeasureModel.allMeasures']
    res={}
    a=allm.ann[0][1]; items=[x for x in a if not isinstance(x,tuple)]
    for i in range(0,len(items),2):
        inner=items[i+1]; b=[x for x in inner.ann[0][1] if not isinstance(x,tuple)]
        for j in range(0,len(b),2):
            r,c=pos(b[j]); res[str(b[j+1])]=f"{colname(c)}{r+1}"
    return res
def addr(f):
    a=f"{colname(f['col'])}{f['row']+1}"
    if (f['row2'],f['col2'])!=(f['row'],f['col']): a+=f"~{colname(f['col2'])}{f['row2']+1}"
    return a
ARGS=re.compile(r"'([^']*)'")
def split_terms(s):
    # split top-level on + / - keeping sign
    out=[];depth=0;cur='';sign='+';q=False
    for ch in s:
        if ch=="'": q=not q
        if not q:
            if ch=='(':depth+=1
            if ch==')':depth-=1
            if ch in '+-' and depth==0 and cur:
                out.append((sign,cur));sign=ch;cur='';continue
        cur+=ch
    out.append((sign,cur));return out
def term_mean(t,mm):
    t=t.strip()
    m=re.match(r"GLQM\('([^']*)'",t)
    if m: return f"科目{m.group(1)}期末余额"
    m=re.match(r"GLLFS\('([^']*)'",t)
    if m: return f"科目{m.group(1)}本年累计发生额"
    m=re.match(r"GLQC\('([^']*)','','(\d*)'",t)
    if m: return f"科目{m.group(1)}期初余额" + (f"（期间参数='{m.group(2)}'，推测=年初/1月期初）" if m.group(2) else "（期间参数留空，推测=当期期初）")
    m=re.match(r"CFLFS\(",t)
    if m:
        a=re.findall(r"'([^']*)'",t); return f"现金流量项目{a[2]}发生额 对方科目{a[-2]}"
    m=re.match(r"CFQC\(.*?,'(\d+)',1",t)
    if m: return f"现金流量项目{m.group(1)}自年初(1月1日)至账套日期累计"
    m=re.match(r"CFFS\(.*?,'(\d+)',1,.*?'(\d*)'",t)
    if m: return f"现金流量项目{m.group(1)}当月发生额(辅助参数'{m.group(2)}')"
    m=re.match(r"PTOTAL\((.*)\)",t)
    if m: return f"区域{m.group(1).replace('?','')}合计"
    m=re.match(r"MSELECTA\((.*?),,(.*)\)",t,re.I)
    if m: return f"同区域{m.group(1)}取{ {'-zmonth()':'上年末（回退当前月份数个月，推测）','-1':'上一期（上月）'}.get(m.group(2),m.group(2)) }指标值"
    m=re.match(r"mselecta\((.*?)\)$",t)
    if m: return f"区域{m.group(1)}当期指标值"
    m=re.match(r"\?([A-Z]+\d+)$",t)
    if m: return f"本表单元格{m.group(1)}"
    return t
def meaning(f,mm):
    c=f['content']
    if c.startswith('IF(zmonth()=1'):
        m=re.search(r"MSELECT\('([^']*)',,-1\)",c)
        tgt=mm.get(m.group(1),'未对齐') if m else '?'
        dm=re.match(r"IF\(zmonth\(\)=1,([A-Z]+\d+),",c).group(1)
        return f"本月数：1月=本年累计{dm}；其他月=本年累计{dm}-上月的指标{tgt}（MSELECT按指标ID取前1期值，推测上月累计）"
    if re.match(r"mselecta\(.*\)-mselecta\(.*,,-1\)",c):
        return "本期金额=本年累计(D列)-上月累计(D列)，逐行区域公式；无1月保护（推测：1月需上期末数为0）"
    if c.startswith('CFLFS('):
        items=sorted(set(re.findall(r"CFLFS\(k\('0+'\),'','','([^']*)'",c)))
        cps=re.findall(r"'((?:[A-Z]%)?)',?''\)",c)
        return f"现金流量项目{items[0]}（对方科目分析法）：按对方科目大类 A%/B%/C%/D%/E% 五段 CFLFS 相加，方向参数=1；取数口径：账套/单位k(000000000000)，期间留空（推测=当期累计）"
    parts=[('' if s=='+' and i==0 else s)+term_mean(t,mm) for i,(s,t) in enumerate(split_terms(c))]
    return ' '.join(parts).replace(' + ','+')
def label_for(cells,r,c,n):
    cands=[c]
    # find nearest text cell leftwards in same block
    if n=='QZ_rep_01': lc=0 if c<=2 else 3
    else: lc=0
    return cells.get((r,lc))
def gen():
    md=[];stat={}
    md.append("# U8C IUFO 报表模板公式解析报告\n")
    md.append("来源：4 个 Java 序列化的 IUFO 报表格式文件（仅模板，无数据）。解析脚本：`parse/jser.py`（Java 序列化流解析器，无需类文件）、`parse/extract.py`、`parse/gen.py`。单元格坐标均来自序列化的 CellPosition（0 基 → 转为 Excel 风格 A1）。公式取自 FormulaModel.public_instantFormula（键=CellPosition 或 AreaPosition → FormulaVO）。\n")
    md.append("通用说明：账套/单位参数 k('000000000000') 为 IUFO 关键字（单位）占位；币种参数'本币'；以下“含义”列为按函数名与参数位置的解释，不确定处标“推测”。科目编码只列编码，不附科目名称（科目表不在文件内）。\n")
    for n in TITLES:
        P,root,cells,forms,other,checks=load(n); mm=measure_map(P)
        forms.sort(key=lambda f:(f['row'],f['col']))
        md.append(f"\n## {TITLES[n]}\n")
        md.append("| 行项目 | 单元格 | 公式（原文） | 含义 |\n|---|---|---|---|")
        aligned=0;un=[]
        cov=set()
        for f in forms:
            lab=label_for(cells,f['row'],f['col'],n)
            if (f['row2'],f['col2'])!=(f['row'],f['col']):
                l2=label_for(cells,f['row2'],f['col2'],n)
                labs=f"{(lab or '').strip()} … {(l2 or '').strip()}（区域{f['row2']-f['row']+1}行）"
            else: labs=(lab or '').strip()
            if lab is None: labs='未对齐'; un.append(addr(f))
            else: aligned+=1
            for r in range(f['row'],f['row2']+1): cov.add((r,f['col']))
            c=f['content'].replace('|','\\|')
            md.append(f"| {labs} | {addr(f)} | `{c}` | {meaning(f,mm)} |")
        stat[n]=dict(total=len(forms),aligned=aligned,un=un)
        if checks:
            md.append("\n报表内置校验公式（SimpleCheckFmlVO）：\n")
            for c in checks: md.append(f"- {c['strfmlName']}：`{c['strFmlContent']}`（错误提示：{c['strErrMsg']}）")
        # label rows without formula
        prim=lambda c:( (1 if c==0 else 4) if n=='QZ_rep_01' else 3)
        nof=[]
        for (r,c),t in sorted(cells.items()):
            if c in ((0,3) if n=='QZ_rep_01' else (0,)) and r>=4:
                pc=prim(c)
                if cells.get((r,pc))=='---' or t.strip()=='---': continue
                if (r,pc) not in cov: nof.append(f"{colname(c)}{r+1}[{t.strip()}]")
        stat[n]['nof']=nof
        md.append(f"\n主取数列（资产负债表B/E列，其余表D列）无公式的标题/行项目共 {len(nof)} 个：{'；'.join(nof)}\n")
    return md,stat
if __name__=='__main__':
    md,stat=gen()
    import json; json.dump(stat,open('stat.json','w'),ensure_ascii=False,indent=1)
    open('body.md','w').write('\n'.join(md))
    for n,s in stat.items(): print(n,s['total'],s['aligned'],s['un'],len(s['nof']))
