#!/usr/bin/env python3
"""產生 App 圖示。

    python3 tools/make_icon.py

輸出到 App/Assets.xcassets/AppIcon.appiconset/AppIcon.png。

設計是「唱片 + 一道琥珀刻度」。刻度不是裝飾 —— 它就是這個 App 要你做的事：
盤面貼個記號、數圈計時。碼錶校準的操作跟圖示講的是同一件事。

**為什麼是這個而不是別的。** 試過的方向與失敗原因（別重踩）：

- **頻閃盤**（唱盤校速的傳統工具，目標族群一眼就懂）—— 60 根細條在 60px 混疊成
  雜訊；改成 12–16 根粗條雖然清楚了，卻讀成相機光圈或齒輪。概念最對，翻譯不過去。
- **抖晃波**（圓周半徑正弦起伏，直接畫出量測對象）—— 任何振幅都讀成齒輪；
  把振幅調小想壓掉齒輪感，結果變成一朵花，連唱片感都沒了。
- **量表指針** —— 讀得清楚，但完全沒有「唱盤」感，跟一堆測速 App 撞臉。

**檢查圖示的正確順序是先看 60px，不是先看 1024。** 大圖好看的方案有一半
在主畫面尺寸下是糊的。
"""
from PIL import Image, ImageDraw
import math, os, sys

SIZE = 1024
SS = 4                    # 超取樣倍率，邊緣才不會有鋸齒
Z = SIZE * SS
C = Z / 2

INK = (28, 28, 30)        # 黑膠黑
CREAM = (243, 240, 232)   # 象牙白
AMBER = (232, 148, 42)    # 老音響 VU 表的琥珀色
GROOVE = (84, 84, 88)


def render() -> Image.Image:
    # 不能有 alpha 通道 —— App Store 會退件。用 RGB 模式從頭到尾就不會有。
    im = Image.new("RGB", (Z, Z), CREAM)
    d = ImageDraw.Draw(im)

    d.ellipse([C - C * .88, C - C * .88, C + C * .88, C + C * .88], fill=INK)

    # 三道粗溝紋。再細就會在 60px 消失，再多就會糊。
    for r in (0.74, 0.60, 0.46):
        d.ellipse([C - C * r, C - C * r, C + C * r, C + C * r],
                  outline=GROOVE, width=int(Z * 0.016))

    # 琥珀刻度：量測用的記號。
    d.polygon([(C - Z * 0.026, C - C * 0.86), (C + Z * 0.026, C - C * 0.86),
               (C + Z * 0.026, C - C * 0.40), (C - Z * 0.026, C - C * 0.40)],
              fill=AMBER)

    # 中心孔。
    d.ellipse([C - C * .135, C - C * .135, C + C * .135, C + C * .135], fill=CREAM)

    return im.resize((SIZE, SIZE), Image.LANCZOS)


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "App", "Assets.xcassets", "AppIcon.appiconset", "AppIcon.png")
    icon = render()
    assert icon.mode == "RGB", f"圖示不能有 alpha 通道，目前是 {icon.mode}"
    icon.save(out)
    print(f"寫入 {out}  ({icon.size[0]}×{icon.size[1]}, {icon.mode})")

    # 小尺寸預覽 —— 主畫面實際上是 60px，那才是要看的尺寸。
    for s in (120, 60, 40):
        print(f"  {s}px 預覽可用 icon.resize(({s},{s}), Image.LANCZOS) 檢查")
    return 0


if __name__ == "__main__":
    sys.exit(main())
