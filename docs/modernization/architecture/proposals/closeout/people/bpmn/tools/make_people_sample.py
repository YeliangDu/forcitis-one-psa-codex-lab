import sys
sys.path.insert(0, sys.argv[1])
from bpmngen import build

OUT = sys.argv[2]


def N(t, name, lane=None, col=0, row=0, doc='', ev=None, **kw):
    d = dict(type=t, name=name, lane=lane, col=col, row=row, doc=doc, ev=ev)
    d.update(kw)
    return d


# ---------------- PRC-83 ----------------
nodes = {
    'Start_1': N('startEvent', '候选人已确认入职日', 'L_hr', 0, ev=('message', 'Msg_confirm'),
                 doc='前序：PRC-82 作出录用决定（AGG-62 录用决定已作出）'),
    'T_register': N('userTask', '登记入职', 'L_hr', 1, doc='AGG-62 登记入职；B-13。入职日为人事业务生效日，可提前登记、补登落回真实日期（U-12）'),
    'E_registered': N('intermediateThrowEvent', '入职已登记', 'L_sys', 2, ev=('signal', 'Sig_registered'),
                      doc='〔缺〕新事件，登记时发出（SAMPLE S3）'),
    'G_split': N('parallelGateway', '', 'L_sys', 3),
    'E_hiredate': N('intermediateCatchEvent', '入职日到达', 'L_sys', 4, row=0, ev=('timer', '入职日 00:00（业务生效日）')),
    'T_effective': N('serviceTask', '执行入职生效', 'L_sys', 5, row=0, doc='AGG-62 〔缺〕入职生效；B-13。补登时立即执行'),
    'E_employed': N('intermediateThrowEvent', 'Employed', 'L_sys', 6, row=0, ev=('signal', 'Sig_employed')),
    'T_assign': N('userTask', '建立任职', 'L_hr', 4, doc='AGG-63 建立任职；B-13。组织单元、职位（职位目录）、职级、汇报线、主 / 兼（Q-08、INV-62）'),
    'G_assign': N('exclusiveGateway', '主任职区间不重叠？', 'L_hr', 5),
    'E_assignchg': N('intermediateThrowEvent', '任职已变更', 'L_sys', 7, row=1, ev=('signal', 'Sig_assign')),
    'T_contract': N('userTask', '提交劳动合同', 'L_hr', 8, doc='AGG-67 提交劳动合同；B-13。用人单位为法人三口径之一（Q-09）'),
    'G_probation': N('exclusiveGateway', '试用期在法定上限内？', 'L_hr', 9, doc='INV-14，劳动合同法第 19 条'),
    'T_approve': N('userTask', '批准劳动合同', 'L_head', 10,
                   doc='AGG-67 批准劳动合同 / 〔缺〕驳回劳动合同；B-13。聚合内单级批准，不经审批平台（intent 114200 F-3）'),
    'G_approve': N('exclusiveGateway', '批准？', 'L_head', 11),
    'T_sign': N('manualTask', '签订劳动合同', 'L_hr', 12, doc='线下纸面签署（where: offline）；接电子签可改为外部系统'),
    'B_month': N('boundaryEvent', '用工满一个月未签', attached='T_sign', cancel=False, ev=('timer', '入职日 + 1 个月'),
                 doc='P-05，劳动合同法第 10、82 条'),
    'T_remind': N('serviceTask', '提醒补签劳动合同', 'L_sys', 12, row=1, doc='P-05 提醒，不拒绝'),
    'End_remind': N('endEvent', '已提醒', 'L_sys', 13, row=1),
    'T_record': N('userTask', '登记劳动合同签订', 'L_hr', 13, doc='AGG-67 签订劳动合同；B-13。证据：已签署合同扫描件、签署日'),
    'E_signed': N('intermediateThrowEvent', '劳动合同已签订', 'L_sys', 14, row=0, ev=('signal', 'Sig_signed')),
    'G_join': N('parallelGateway', '', 'L_sys', 15, row=0),
    'End_ok': N('endEvent', '已入职', 'L_sys', 16, row=0),
}
flows = [
    ('F1', 'Start_1', 'T_register', ''), ('F2', 'T_register', 'E_registered', ''), ('F3', 'E_registered', 'G_split', ''),
    ('F4', 'G_split', 'E_hiredate', ''), ('F5', 'E_hiredate', 'T_effective', ''), ('F6', 'T_effective', 'E_employed', ''),
    ('F7', 'E_employed', 'G_join', ''),
    ('F8', 'G_split', 'T_assign', ''), ('F9', 'T_assign', 'G_assign', ''),
    ('F10', 'G_assign', 'E_assignchg', '不重叠'), ('F11', 'G_assign', 'T_assign', '重叠，改正'),
    ('F12', 'E_assignchg', 'T_contract', ''), ('F13', 'T_contract', 'G_probation', ''),
    ('F14', 'G_probation', 'T_approve', '在上限内'), ('F15', 'G_probation', 'T_contract', '超上限，改合同'),
    ('F16', 'T_approve', 'G_approve', ''), ('F17', 'G_approve', 'T_sign', '已批准'), ('F18', 'G_approve', 'T_contract', '已驳回'),
    ('F19', 'T_sign', 'T_record', ''), ('F20', 'B_month', 'T_remind', ''), ('F21', 'T_remind', 'End_remind', ''),
    ('F22', 'T_record', 'E_signed', ''), ('F23', 'E_signed', 'G_join', ''), ('F24', 'G_join', 'End_ok', ''),
]
msgs = [('M1', 'P_cand', 'Start_1', '入职日确认'), ('M2', 'T_sign', 'P_cand', '劳动合同文本'),
        ('M3', 'P_cand', 'T_sign', '已签署的劳动合同')]
xml = build('PRC-83', '办理入职与任职',
            lanes=[('L_hr', '人力（SH-E6）', 1), ('L_head', '人力负责人（SH-E6）', 1), ('L_sys', '系统', 2)],
            nodes=nodes, flows=flows, msgs=msgs, pools_top=[('P_cand', '候选人（SH-H1）')],
            stores=[('DS_62', 'AGG-62 人员', 1, ['T_register', 'T_effective']), ('DS_63', 'AGG-63 任职', 4, ['T_assign']),
                    ('DS_67', 'AGG-67 劳动合同', 10, ['T_contract', 'T_approve', 'T_record'])],
            objects=[('DO_contract', '已签署合同扫描件＋签署日', 13, ['T_record'])],
            annotations=[('A_reg', '→ PRC-137 入职开通企业微信并绑定外部身份（platform.identity）', 2, 0, 'E_registered'),
                         ('A_emp', '→ PRC-84 参保增员与建立薪酬档案（B-15）；PRC-137 放行登录', 6, 0, 'E_employed'),
                         ('A_asg', '→ platform.identity 重算默认角色与数据范围', 7, 60, 'E_assignchg'),
                         ('A_sig', '→ PRC-145 法人三口径预警（B-15）', 14, 0, 'E_signed')],
            signals=[('Sig_registered', '入职已登记'), ('Sig_employed', 'Employed'), ('Sig_assign', '任职已变更'),
                     ('Sig_signed', '劳动合同已签订')],
            messages=[('Msg_confirm', '入职日确认')],
            doc='owner B-13；样板 SAMPLE.md；VS-07.3')
open(f'{OUT}/PRC-83.bpmn', 'w').write(xml)

# ---------------- PRC-137 ----------------
nodes = {
    'Start_1': N('startEvent', '入职已登记', 'L_sys', 0, ev=('signal', 'Sig_registered'), doc='来自 PRC-83（B-13 AGG-62）'),
    'T_pending': N('serviceTask', '建立待开通登录身份', 'L_sys', 1,
                   doc='platform.identity 〔缺〕建立待开通登录身份。以人员 ID 为键，拟定 userid = 工号，登录放行起点 = 入职日'),
    'T_open': N('sendTask', '开通企业微信成员', 'L_sys', 2,
                doc='platform.identity 〔缺〕开通企业微信成员，经 platform.integration 企业微信适配器（external:企业微信）；入职日前 3 个工作日自动开通（用户已定 T-1、T-2）'),
    'E_result': N('intermediateCatchEvent', '收到开通结果', 'L_sys', 3, ev=('message', 'Msg_result')),
    'G_ok': N('exclusiveGateway', '开通成功？', 'L_sys', 4),
    'T_bind': N('serviceTask', '绑定外部身份', 'L_sys', 5, doc='platform.identity 〔缺〕绑定外部身份。按 userid 精确绑定，不模糊匹配'),
    'E_employed': N('intermediateCatchEvent', 'Employed', 'L_sys', 6, ev=('signal', 'Sig_employed'), doc='来自 PRC-83，入职日到达'),
    'T_allow': N('serviceTask', '放行登录', 'L_sys', 7, doc='platform.identity；不设口令兜底（Q-03、SECURITY_INVARIANTS）'),
    'End_ok': N('endEvent', '账号可用', 'L_sys', 8),
    'T_fix': N('userTask', '处理开通失败', 'L_it', 4, doc='信息部门 1 个工作日内修正（如 userid 被占用、接口报错）后重试（用户已定 T-2）'),
    'B_due': N('boundaryEvent', '1 个工作日未处理', attached='T_fix', cancel=False, ev=('timer', '失败后 1 个工作日')),
    'T_alarm': N('serviceTask', '告警人力与信息部门', 'L_hr', 5, doc='不静默（CLAUDE.md 第 6 条）'),
    'End_alarm': N('endEvent', '已告警', 'L_hr', 6),
}
flows = [('F1', 'Start_1', 'T_pending', ''), ('F2', 'T_pending', 'T_open', ''), ('F3', 'T_open', 'E_result', ''),
         ('F4', 'E_result', 'G_ok', ''), ('F5', 'G_ok', 'T_bind', '成功'), ('F6', 'G_ok', 'T_fix', '失败'),
         ('F7', 'T_fix', 'T_open', '已修正，重试'), ('F8', 'T_bind', 'E_employed', ''), ('F9', 'E_employed', 'T_allow', ''),
         ('F10', 'T_allow', 'End_ok', ''), ('F11', 'B_due', 'T_alarm', ''), ('F12', 'T_alarm', 'End_alarm', '')]
msgs = [('M1', 'T_open', 'P_wecom', '创建成员（userid = 工号）'), ('M2', 'P_wecom', 'E_result', '开通结果')]
xml = build('PRC-137', '入职开通企业微信账号并绑定外部身份',
            lanes=[('L_sys', '系统', 1), ('L_it', '信息（SH-E6）', 1), ('L_hr', '人力（SH-E6）', 1)],
            nodes=nodes, flows=flows, msgs=msgs, pools_top=[('P_wecom', '企业微信')],
            annotations=[('A_t1', '用户已定：入职日前 3 个工作日自动开通；失败由信息部门 1 个工作日内处理', 2, 0, 'T_open')],
            signals=[('Sig_registered', '入职已登记'), ('Sig_employed', 'Employed')],
            messages=[('Msg_result', '开通结果')],
            doc='owner platform.identity（S-14）；Q-03；VS-07.3')
open(f'{OUT}/PRC-137.bpmn', 'w').write(xml)

# ---------------- PRC-84 ----------------
nodes = {
    'Start_1': N('startEvent', 'Employed', 'L_sys', 0, ev=('signal', 'Sig_employed'), doc='来自 PRC-83（B-13 AGG-62）'),
    'G_need': N('exclusiveGateway', '该用工类型需要参保？', 'L_hr', 1, doc='退休返聘与实习不参保（用户已定 T-4）'),
    'T_enroll': N('manualTask', '办理社保与公积金增员', 'L_hr', 2, doc='在人社与公积金官方网办平台办理（where: offline）'),
    'B_30': N('boundaryEvent', '入职满 30 日未办', attached='T_enroll', cancel=False, ev=('timer', '入职日 + 30 日'),
              doc='P-32，社会保险法第 58 条'),
    'T_remind': N('serviceTask', '提醒办理参保', 'L_sys', 3, doc='P-32 提醒'),
    'End_remind': N('endEvent', '已提醒', 'L_sys', 4),
    'T_record': N('userTask', '登记参保增员', 'L_hr', 3, doc='AGG-74 参保增员；B-15。证据：社保与公积金增员回执、办理日'),
    'G_merge': N('exclusiveGateway', '', 'L_hr', 4),
    'T_profile': N('userTask', '建立薪酬档案', 'L_hr', 5, doc='AGG-93 建立薪酬档案；B-15。生效日 = 入职日（U-12）；敏感个人信息（ADR-0043）'),
    'E_profile': N('intermediateThrowEvent', '薪酬档案已变更', 'L_sys', 6, ev=('signal', 'Sig_profile')),
    'End_ok': N('endEvent', '已参保并建档', 'L_sys', 7),
}
flows = [('F1', 'Start_1', 'G_need', ''), ('F2', 'G_need', 'T_enroll', '需要'), ('F3', 'G_need', 'G_merge', '不需要'),
         ('F4', 'T_enroll', 'T_record', ''), ('F5', 'T_record', 'G_merge', ''), ('F6', 'G_merge', 'T_profile', ''),
         ('F7', 'T_profile', 'E_profile', ''), ('F8', 'E_profile', 'End_ok', ''), ('F9', 'B_30', 'T_remind', ''),
         ('F10', 'T_remind', 'End_remind', '')]
msgs = [('M1', 'T_enroll', 'P_gov', '增员申报'), ('M2', 'P_gov', 'T_enroll', '增员回执')]
xml = build('PRC-84', '入职后参保增员与建立薪酬档案',
            lanes=[('L_hr', '人力（SH-E6）', 1), ('L_sys', '系统', 1)],
            nodes=nodes, flows=flows, msgs=msgs, pools_bottom=[('P_gov', '人社与公积金经办平台（SH-R2）')],
            stores=[('DS_74', 'AGG-74 社保与公积金参保', 3, ['T_record']), ('DS_93', 'AGG-93 人员薪酬档案', 5, ['T_profile'])],
            objects=[('DO_receipt', '增员回执＋办理日', 3, ['T_record'])],
            annotations=[('A_p', '→ PRC-145 法人三口径预警（B-15）', 6, 0, 'E_profile')],
            signals=[('Sig_employed', 'Employed'), ('Sig_profile', '薪酬档案已变更')],
            doc='owner B-15；VS-07.3')
open(f'{OUT}/PRC-84.bpmn', 'w').write(xml)
print('done')
