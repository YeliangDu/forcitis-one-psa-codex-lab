# BC: (id, name, aggs, kind[thin/deep/touch], engine_extra_LMH, mig[none/norm/heavy], built_credit, h5pages)
B=[
("B-13","人员与组织(人事记录)",9,"deep",(1500,3000,4500),"heavy",0.15,2),
("B-14","工作日历(已建)",2,"thin",(0,0,0),"none",0.9,0),
("B-01","往来单位",4,"thin",(0,0,0),"norm",0,0),
("B-02","服务与定价目录",5,"thin",(0,0,0),"norm",0,0),
("B-30","知识(薄版)",1,"thin",(0,0,0),"norm",0,0),
("B-19","会计期间",2,"deep",(500,1000,1500),"norm",0,0),
("B-16","过账",1,"deep",(3000,5000,8000),"heavy",0,0),
("B-20","总账",8,"deep",(3000,5000,8000),"norm",0,0),
("B-03","售前",7,"deep",(0,0,0),"norm",0,0),
("B-04","交付执行",11,"deep",(500,1500,2500),"heavy",0,1),
("B-28","质量与认可",7,"deep",(0,0,0),"norm",0,1),
("B-27","请假与加班",5,"thin",(0,0,0),"norm",0,2),
("B-18","计划与预算",2,"thin",(500,1000,2000),"norm",0,0),
("B-05","合同",5,"deep",(0,0,0),"norm",0,0),
("B-06","工时",1,"touch",(1000,2000,3000),"heavy",0.25,2),
("B-08","开票与收入",4,"touch",(2000,4000,6000),"heavy",0,0),
("B-22","银行与现金",2,"touch",(1000,1500,2500),"norm",0,0),
("B-23","税费",2,"touch",(1000,2000,3500),"norm",0,0),
("B-09","应收与收款",3,"touch",(0,500,1000),"heavy",0,0),
("B-10","应付与付款",3,"touch",(0,500,1000),"heavy",0,0),
("B-11","分包与采购",4,"touch",(0,0,0),"norm",0,0),
("B-12","费用报销",1,"touch",(500,1000,2000),"norm",0,2),
("B-15","薪酬",6,"touch",(3000,5000,8000),"heavy",0,1),
("B-07","成本核算",4,"touch",(1500,3000,5000),"none",0,0),
("B-17","口径与勾稽",2,"deep",(1000,2000,3000),"none",0,0),
("B-21","法定报表",1,"thin",(500,1000,2000),"none",0,0),
("B-24","固定资产",1,"touch",(500,1000,1500),"norm",0,0),
("B-26","检查",4,"deep",(0,0,0),"none",0,1),
("B-29","项目结项(流程模块)",2,"thin",(0,0,0),"none",0,0),
]
PER_AGG={"thin":(600,850,1200),"deep":(1100,1600,2400),"touch":(1100,1600,2400)}
TOUCH_BC=(800,1500,2500)
FIX=(300,500,800)
TR={"thin":(0.9,1.0,1.2),"deep":(1.2,1.35,1.5),"touch":(1.3,1.5,1.7)}
SQLA={"thin":(50,70,100),"deep":(80,110,160),"touch":(80,110,160)}
SQLT=(100,200,300)
MIG={"none":(0,0,0),"norm":(600,1000,1600),"heavy":(1800,3000,4500)}
MIGTR=(1.2,1.4,1.6); MIGSQL=(0.05,0.08,0.12)
PG_RUN=(220,350,550); PG_TEST=(150,220,350)
def pages(a,k,i):
    base = a*1.0+1 if k=="thin" else a*1.5+2
    return base*(0.8,1.0,1.3)[i]
rows=[];tot=[[0]*7 for _ in range(3)]
for bid,name,a,k,eng,mig,cred,h5 in B:
    r=[]
    for i in range(3):
        m=a*PER_AGG[k][i]+FIX[i]+eng[i]+(TOUCH_BC[i] if k=="touch" else 0)
        t=m*TR[k][i]
        s=a*SQLA[k][i]+(SQLT[i] if k=="touch" else 0)
        mg=MIG[mig][i]; mgt=mg*MIGTR[i]; s+=mg*MIGSQL[i]
        pg=pages(a,k,i)+h5
        f=pg*PG_RUN[i]; ft=pg*PG_TEST[i]
        c=1-cred
        v=[m*c,t*c,s*c,mg,mgt,f*c,ft*c]
        for j in range(7): tot[i][j]+=v[j]
        r.append((v,pg))
    rows.append((bid,name,a,k,r))
X=[ # name, main, testratio, sql, fe pages
("X-MIG 迁入编排 platform.migration",(2500,4000,6000),(1.2,1.4,1.6),(150,300,500),(3,5,8)),
("X-EVT 事件机制实施项",(1500,2500,4000),(1.2,1.4,1.6),(50,100,200),(2,3,4)),
("X-RPT reporting(R1-R6)",(5000,9000,15000),(0.6,0.8,1.0),(1500,3000,5000),(20,30,45)),
("X-ADM A8 行政流程线",(4000,8000,15000),(0.8,1.0,1.2),(300,600,1000),(15,25,40)),
("X-ARC 老库只读归档与调阅",(1000,2000,3500),(1.0,1.2,1.4),(100,200,400),(2,4,6)),
("X-CUT 切换/重算比对/对账工具",(2000,4000,7000),(0.8,1.0,1.2),(200,500,1000),(2,4,6)),
]
xrows=[]
for n,m,tr,s,pg in X:
    r=[]
    for i in range(3):
        v=[m[i],m[i]*tr[i],s[i],0,0,pg[i]*PG_RUN[i],pg[i]*PG_TEST[i]]
        for j in range(7): tot[i][j]+=v[j]
        r.append((v,pg[i]))
    xrows.append((n,r))
import json
print("| BC | 名称 | 聚合 | 类 | 运行(中) | 测试(中) | SQL(中) | 迁入适配(运行+测试,中) | 前端页(中) | 前端(运行+测试,中) | 合计 低 / 中 / 高 |")
print("|---|---|---|---|---|---|---|---|---|---|---|")
def k(x): return f"{x/1000:.1f}k"
for bid,name,a,kd,r in rows:
    v,pg=r[1]; t=[sum(rr[0]) for rr in r]
    print(f"| {bid} | {name} | {a} | {kd} | {k(v[0])} | {k(v[1])} | {k(v[2])} | {k(v[3]+v[4])} | {pg:.0f} | {k(v[5]+v[6])} | {k(t[0])} / {k(t[1])} / {k(t[2])} |")
for n,r in xrows:
    v,pg=r[1]; t=[sum(rr[0]) for rr in r]
    print(f"| {n} | | | | {k(v[0])} | {k(v[1])} | {k(v[2])} | — | {pg} | {k(v[5]+v[6])} | {k(t[0])} / {k(t[1])} / {k(t[2])} |")
lab=["后端运行","后端测试","SQL","迁入适配运行","迁入适配测试","前端运行","前端测试"]
for i,nm in enumerate(["低","中","高"]):
    print(nm, {lab[j]:round(tot[i][j]) for j in range(7)}, "合计",round(sum(tot[i])), "运行合计", round(tot[i][0]+tot[i][3]+tot[i][5]+tot[i][2]))
pgs=[sum(r[i][1] for *_,r in rows)+sum(r[i][1] for _,r in xrows) for i in range(3)]
print("pages",pgs)
