#!/usr/bin/env python3
"""由 state-transitions 生成 process-steps 提议稿：每个「有 guard 的状态跃迁」对应一个流程步骤 + 一条 Command。

规则（P-1，见 validate_concerns.py）：
  07 业务流程的每一步只要改变对象状态，必须声明其 Command（BPMN 的 Task ≠ DDD 的 Command）。
本脚本不发明真相：command 名按约定生成并标 naming: convention，prc 留空标 needs_review，
待流程目录（L2/L3）逐条认领后转正。输出 process-steps.proposal.yaml。
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

VERB = {
    # 协议 OBJ-05
    ('OBJ-05', '草案', '审批中'): 'SubmitAgreementForApproval',
    ('OBJ-05', '审批中', '已签署生效'): 'SignAgreement',
    ('OBJ-05', '审批中', '草案'): 'ReturnAgreementForRevision',
    ('OBJ-05', '已签署生效', '履行中'): 'StartPerformance',
    ('OBJ-05', '履行中', '已完成'): 'CompleteServices',
    ('OBJ-05', '已完成', '已结算'): 'SettleAgreement',
    ('OBJ-05', '*', '已终止'): 'TerminateAgreement',
    ('OBJ-05', '*', '无效'): 'DeclareAgreementVoid',
    # 项目 OBJ-29
    ('OBJ-29', '已立项', '执行中'): 'StartProject',
    ('OBJ-29', '执行中', '已暂停'): 'SuspendProject',
    ('OBJ-29', '已暂停', '执行中'): 'ResumeProject',
    ('OBJ-29', '执行中', '收尾中'): 'StartCloseOut',
    ('OBJ-29', '收尾中', '已结项'): 'CloseProject',
    ('OBJ-29', '已结项', '已归档'): 'ArchiveProject',
    ('OBJ-29', '*', '已终止'): 'TerminateProject',
    ('OBJ-29', '已立项', '已取消'): 'CancelProject',
    # 工时单 OBJ-66
    ('OBJ-66', '草稿', '已提交'): 'SubmitTimesheet',
    ('OBJ-66', '已提交', '已通过'): 'ApproveTimesheet',
    ('OBJ-66', '已提交', '已退回'): 'ReturnTimesheet',
    ('OBJ-66', '已提交', '已驳回'): 'RejectTimesheet',
    ('OBJ-66', '已退回', '草稿'): 'ReopenTimesheet',
    ('OBJ-66', '已驳回', '草稿'): 'ReopenTimesheet',
    ('OBJ-66', '已提交', '草稿'): 'WithdrawTimesheet',
    ('OBJ-66', '已通过', '已过账'): 'PostTimesheet',
    # 开票申请 OBJ-82
    ('OBJ-82', '草稿', '审批中'): 'SubmitBillingRequest',
    ('OBJ-82', '审批中', '准入通过'): 'ApproveBillingRequest',
    ('OBJ-82', '审批中', '驳回'): 'RejectBillingRequest',
    ('OBJ-82', '草稿', '撤回'): 'WithdrawBillingRequest',
    ('OBJ-82', '准入通过', '已开票'): 'IssueInvoice',
    ('OBJ-82', '准入通过', '作废'): 'VoidBillingRequest',
    # 增值税发票 OBJ-83
    ('OBJ-83', '已开具', '部分红冲'): 'CreditNotePartial',
    ('OBJ-83', '已开具', '全额红冲'): 'CreditNoteFull',
    ('OBJ-83', '已取得', '已用途确认'): 'ConfirmInvoiceUsage',
    ('OBJ-83', '已用途确认', '已入账'): 'PostInputInvoice',
    ('OBJ-83', '已入账', '进项转出'): 'TransferOutInputTax',
    ('OBJ-83', '已用途确认', '红冲确认中'): 'RequestCreditNote',
    ('OBJ-83', '红冲确认中', '已开具'): 'ExpireCreditNoteConfirmation',
    # 会计期间 OBJ-97
    ('OBJ-97', '未开', '开放'): 'OpenPeriod',
    ('OBJ-97', '开放', '关账中'): 'StartPeriodClose',
    ('OBJ-97', '关账中', '已关账'): 'ClosePeriod',
    ('OBJ-97', '已关账', '开放'): 'ReopenPeriod',
    # 用印申请 OBJ-113
    ('OBJ-113', '草稿', '审批中'): 'SubmitSealRequest',
    ('OBJ-113', '审批中', '已批准'): 'ApproveSealRequest',
    ('OBJ-113', '审批中', '已驳回'): 'RejectSealRequest',
    ('OBJ-113', '已批准', '已用印'): 'ApplySeal',
    ('OBJ-113', '已用印', '已关闭'): 'CloseSealRequest',
    # 月度薪酬结算 OBJ-73
    ('OBJ-73', '草稿', '已计算'): 'CalculatePayroll',
    ('OBJ-73', '已计算', '审核中'): 'SubmitPayrollForReview',
    ('OBJ-73', '审核中', '已批准'): 'ApprovePayroll',
    ('OBJ-73', '已批准', '已发放'): 'PayPayroll',
    ('OBJ-73', '已发放', '已过账'): 'PostPayroll',
}


def load(p):
    import yaml
    return yaml.safe_load(Path(p).read_text(encoding='utf8'))


def main():
    tr = load(HERE / 'state-transitions.proposal.yaml')
    out, missing = [], []
    n = 0
    for o in tr.get('objects') or []:
        obj = o.get('obj')
        for t in o.get('transitions') or []:
            if not t.get('guard') or t.get('guard') == 'free':
                continue
            n += 1
            cmd = VERB.get((obj, t['from'], t['to'])) or VERB.get((obj, '*', t['to']))
            if not cmd:
                missing.append((obj, t['from'], t['to']))
                cmd = 'TODO_Command'
            out.append({'id': f'PS-{len(out)+1:03d}', 'obj': obj,
                        'from': t['from'], 'to': t['to'], 'guard': t['guard'],
                        'command': cmd, 'prc': None,
                        'naming': 'convention', 'needs_review': True,
                        'status': 'proposal'})

    head = f'''# process-steps.proposal.yaml — 流程步骤 ↔ 对象状态跃迁 ↔ Command（提议稿）
#
# 为什么要有这个文件：P-1 规则（validate_concerns.py）
#   BPMN 的 Task 与 DDD 的 Command 不是一一对应，必须显式映射；否则流程目录与对象模型各写一套。
# 本稿由 工具 生成：每条「有 guard 的状态跃迁」对应一个步骤；
#   command 名按约定生成（naming: convention），prc 留空（needs_review: true），
#   待流程目录（L2/L3，PRC-ID）逐条认领后转正。
# 统计：跃迁 {n} 处，command 已填 {n - len(missing)}，待补 {len(missing)}。
# normative: false

version: 0.1
status: proposal
normative: false
updated: '2026-10-19'
sources:
  transitions: state-transitions.proposal.yaml
  rule: validate_concerns.py P-1

steps:
'''
    body = ''
    for s in out:
        body += (f"  - {{id: {s['id']}, obj: {s['obj']}, from: '{s['from']}', to: '{s['to']}', "
                 f"guard: '{s['guard']}', command: {s['command']}, prc: null, "
                 f"naming: {s['naming']}, needs_review: true}}\n")
    (HERE / 'process-steps.proposal.yaml').write_text(head + body, encoding='utf8')
    print(f"process-steps: {n} 步，command 已填 {n-len(missing)}，待补 {len(missing)}" +
          (f"，缺动词: {missing}" if missing else ''))


if __name__ == '__main__':
    main()
