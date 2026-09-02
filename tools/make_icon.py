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


def render_adaptive_foreground() -> Image.Image:
    """Android 自適應圖示的前景層。

    **外圈會被系統遮罩切掉。** 各家 launcher 用的形狀不同（圓、方角、水滴），
    只有中央約 66% 的「安全區」保證看得到 —— 所以唱片要縮到那個範圍內，
    而且背景層要另外給（純色），不能像 iOS 那樣整張滿版。
    """
    z = 432 * SS
    im = Image.new("RGBA", (z, z), (0, 0, 0, 0))
    disc = render().resize((int(z * 0.66), int(z * 0.66)), Image.LANCZOS).convert("RGBA")
    # 唱片本身是方形的象牙白底 + 黑圓，把方形底去掉只留圓。
    mask = Image.new("L", disc.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, disc.size[0] - 1, disc.size[1] - 1], fill=255)
    disc.putalpha(mask)
    off = (z - disc.size[0]) // 2
    im.paste(disc, (off, off), disc)
    return im.resize((432, 432), Image.LANCZOS)


def write_android(root: str) -> None:
    res = os.path.join(root, "android", "app", "src", "main", "res")
    if not os.path.isdir(res):
        print("  (沒有 android/，略過)")
        return
    icon = render()

    # 舊版（API < 26）用滿版的方形圖示；圓形版本自己裁圓。
    for folder, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                       ("xxhdpi", 144), ("xxxhdpi", 192)):
        d = os.path.join(res, f"mipmap-{folder}")
        os.makedirs(d, exist_ok=True)
        square = icon.resize((px, px), Image.LANCZOS)
        square.save(os.path.join(d, "ic_launcher.png"))
        rounded = square.convert("RGBA")
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        rounded.putalpha(mask)
        rounded.save(os.path.join(d, "ic_launcher_round.png"))
        # 精靈留下的 webp 會蓋掉同名 png，要刪掉
        for stale in ("ic_launcher.webp", "ic_launcher_round.webp"):
            f = os.path.join(d, stale)
            if os.path.exists(f):
                os.remove(f)

    # 自適應圖示：前景 PNG + 純色背景
    fg = os.path.join(res, "drawable", "ic_launcher_foreground.png")
    os.makedirs(os.path.dirname(fg), exist_ok=True)
    render_adaptive_foreground().save(fg)
    for stale in ("ic_launcher_foreground.xml", "ic_launcher_background.xml"):
        f = os.path.join(res, "drawable", stale)
        if os.path.exists(f):
            os.remove(f)

    colors = os.path.join(res, "values", "ic_launcher_background.xml")
    with open(colors, "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                f'    <color name="ic_launcher_background">#{CREAM[0]:02X}{CREAM[1]:02X}{CREAM[2]:02X}</color>\n'
                '</resources>\n')

    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(res, "mipmap-anydpi", name), "w") as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                    '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                    '    <background android:drawable="@color/ic_launcher_background" />\n'
                    '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                    '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
                    '</adaptive-icon>\n')
    print(f"寫入 Android 圖示到 {res}")


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "App", "Assets.xcassets", "AppIcon.appiconset", "AppIcon.png")
    icon = render()
    assert icon.mode == "RGB", f"圖示不能有 alpha 通道，目前是 {icon.mode}"
    icon.save(out)
    print(f"寫入 {out}  ({icon.size[0]}×{icon.size[1]}, {icon.mode})")

    write_android(root)

    # 小尺寸預覽 —— 主畫面實際上是 60px，那才是要看的尺寸。
    for s in (120, 60, 40):
        print(f"  {s}px 預覽可用 icon.resize(({s},{s}), Image.LANCZOS) 檢查")
    return 0


if __name__ == "__main__":
    sys.exit(main())
