## 原话

合并，照这样改（研发基建并入 one-ops）

## 落点

研发基建实现（scripts/hooks、scripts/merge、scripts/ci、scripts/precheck.sh、.github/workflows）归 one-ops，代码交 Codex、钩子与解析放后台子代理，one-ops 仍随时待命；规则与守护的设计、核查、复核留 one-quality，安全门 opus 复审；one-merge 只做队列运营；在途卡收完当前一步再交接，未开工的 MRG-02/03/04/08/09/10/11、QUA-05、QUA-07 改归 one-ops
