#!/usr/bin/env python3
"""errand-service E2E：通过业务应用 SSE 代理调用 DSH Agent，验证 5 个工具全链路。"""
import json, subprocess, sys

AGENT = "errand-copilot"
URL = "http://127.0.0.1:18116/api/assistant/stream"

CASES = [
    ("T1 服务查询", "跑腿平台有哪些跑腿服务？各多少钱？简洁回答", ["文档取送", "蛋糕配送"]),
    ("T2 跑腿员查询", "跑腿平台有哪些跑腿员？谁评分最高？简洁回答", ["阿捷", "5.0"]),
    ("T3 下单", "我是测试客户钱先生，电话13100007777，要在跑腿平台下单一笔代购跑腿，从山姆代购一箱牛奶送到幸福小区，帮我下单，告诉我单号和价格", ["E7", "代购"]),
    ("T4 订单查询", "查一下跑腿订单 E7005，谁下的？简洁回答", ["齐女士", "蛋糕"]),
    ("T5 运营统计", "跑腿平台今天运营情况怎么样？接了多少单？简洁回答", ["接", "营收"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== errand-service E2E: {passed}/{len(cases)} PASS =====")

if __name__ == "__main__":
    main()
