#!/usr/bin/env python3
"""取证结果的姓名样式扫描：只报「文件:行号:命中个数」，不打印行内容（ADR-0037 底线①，错题本 DATA-018）。

命中口径：括号、引号、书名号内恰为 2–3 个汉字且首字取自常见姓氏的片段（姓氏表 SURNAMES）；宁多报勿漏报。
命中后由人去源文件里看那一行，看之前先确认不会把内容贴进会话。
用法：name_pattern_scan.py FILE [FILE...] | name_pattern_scan.py --self-test
退出码：有命中为 1，无命中为 0，文件读不了为 2。
"""
import re
import sys

SURNAMES = "王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾萧田董袁潘于蒋蔡余杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤"
FRAGMENT = re.compile(r"[\[\(（【「『\"“'‘]\s*([" + SURNAMES + r"][一-鿿]{1,2})\s*[\]\)）】」』\"”'’]")


def scan_text(text):
    return [(n, len(FRAGMENT.findall(line))) for n, line in enumerate(text.splitlines(), 1) if FRAGMENT.search(line)]


def self_test():
    assert scan_text("条件 [张三] 通过") == [(1, 1)]
    assert scan_text("「李四」「王五」") == [(1, 2)]
    assert scan_text("状态 [已提交] 无") == []
    assert scan_text("[ab] (12)") == []
    print("self-test ok")


def main(argv):
    if argv == ["--self-test"]:
        self_test()
        return 0
    if not argv:
        print(__doc__)
        return 2
    hit = False
    for path in argv:
        try:
            with open(path, encoding="utf-8") as f:
                text = f.read()
        except OSError as e:
            print(f"{path}: 读不了（{e.strerror}）")
            return 2
        for number, count in scan_text(text):
            print(f"{path}:{number}:{count}")
            hit = True
    return 1 if hit else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
