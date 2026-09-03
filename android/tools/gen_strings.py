#!/usr/bin/env python3
"""從 strings_catalog.json 產生四個語系的 strings.xml。

    python3 tools/gen_strings.py . tools/strings_catalog.json

**四個 strings.xml 不要手改** —— 改目錄檔再重跑，才保證四個語系的鍵完全一致、
位置參數齊全。手改其中一個語系不會有任何東西擋下來，那正是 CLAUDE.md 坑 29
描述的那種靜默失敗。

原始說明：


catalog.json 是一個陣列，每項： {key, zh, en, ja, de, args}
`args` 是格式參數的個數（0 代表沒有）。

**有參數的字串一律用位置參數 %1$s**：Android 的 aapt2 在同一條字串裡出現
兩個以上非位置參數時會直接報錯，而且日文與德文常常要換順序
（「相比%1$@了 %2$.3f 個百分點」在日文是「%2$.3f ポイント%1$@ました」）。
沒有參數的字串裡的 % 保持原樣 —— getString(id) 不做格式化。
"""
import json, pathlib, sys, re

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
CATALOG = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "catalog.json")

DIRS = {"en": "values", "zh": "values-zh-rTW", "ja": "values-ja", "de": "values-de"}


def escape(s: str) -> str:
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"')
    s = s.replace("\n", "\\n")
    return s


def main():
    # 傳錯根目錄會在 repo 根建出一個 app/ —— 而 macOS 的檔名不分大小寫，
    # 那個 app/ 就是 iOS 的 App/，等於把產生的檔案倒進 Swift 原始碼裡。
    assert (ROOT / "app/build.gradle.kts").exists(), f"{ROOT} 不是 android 模組的根目錄"

    rows = json.loads(CATALOG.read_text())
    keys = [r["key"] for r in rows]
    assert len(keys) == len(set(keys)), "key 重複：" + str(
        [k for k in keys if keys.count(k) > 1][:5])

    for lang, folder in DIRS.items():
        out = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
        if lang == "en":
            out.append('    <string name="app_name">TurntableRPM</string>')
        for r in rows:
            v = r.get(lang) or r["zh"]
            n = r.get("args", 0)
            if n:
                # 位置參數必須齊全，否則 aapt2 會擋下來。
                found = set(re.findall(r"%(\d+)\$", v))
                missing = {str(i) for i in range(1, n + 1)} - found
                assert not missing, f"{r['key']} / {lang} 缺少參數 {sorted(missing)}：{v}"
            out.append(f'    <string name="{r["key"]}">{escape(v)}</string>')
        out.append("</resources>")
        path = ROOT / "app/src/main/res" / folder / "strings.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(out) + "\n")
        print(f"{folder}/strings.xml  {len(rows)} 條")


if __name__ == "__main__":
    main()
