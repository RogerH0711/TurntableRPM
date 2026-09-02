#!/usr/bin/env python3
"""分析 App 匯出的量測 JSON。

用法：  python3 tools/analyze_export.py TurntableRPM-20260831-011600.json

重點在磁力計那一段：把水平投影的軌跡當成圓來擬合，並且**分段擬合**看圓心會不會
隨時間漂移。圓心若是固定的（手機自帶磁鐵），扣掉就能還原轉角；圓心若一直在動，
代表讀數在量測過程中被改動過，那條路就不能用。
"""
import json, math, sys


def load(path):
    with open(path) as f:
        d = json.load(f)
    cols = {name: i for i, name in enumerate(d["columns"])}
    return d, cols, d["samples"]


def basis_from_gravity(g):
    """跟 MagneticRevolutionCounter.makeBasis 同一套：由重力方向建水平面正交基底。"""
    m = math.sqrt(sum(c * c for c in g))
    axis = [c / m for c in g]
    ax, ay, az = (abs(c) for c in axis)
    seed = [1, 0, 0] if (ax <= ay and ax <= az) else ([0, 1, 0] if ay <= az else [0, 0, 1])
    def cross(a, b):
        return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
    e1 = cross(seed, axis)
    n = math.sqrt(sum(c * c for c in e1))
    e1 = [c / n for c in e1]
    return e1, cross(axis, e1)


def project(samples, cols, prefix):
    """把磁場投影到水平面。回傳 [(x, y)]，缺值的樣本跳過。

    垂直分量一定要用「當下這一筆」的重力扣掉。只用固定基底做內積的話，
    強磁場的垂直分量會隨盤面章動洩漏進來 —— 實測 470 µT 的垂直場配上
    1.8° 的軸傾斜就洩漏 15 µT，而水平訊號只有 25 µT。
    """
    g0 = [samples[0][cols[k]] for k in ("gx", "gy", "gz")]
    e1, e2 = basis_from_gravity(g0)
    pts = []
    for s in samples:
        v = [s[cols[prefix + a]] for a in ("x", "y", "z")]
        if any(c is None for c in v):
            continue
        g = [s[cols[k]] for k in ("gx", "gy", "gz")]
        gm = math.sqrt(sum(c * c for c in g))
        if gm < 1e-9:
            continue
        down = [c / gm for c in g]
        vert = sum(a * b for a, b in zip(v, down))
        h = [v[i] - vert * down[i] for i in range(3)]
        pts.append((sum(a * b for a, b in zip(h, e1)),
                    sum(a * b for a, b in zip(h, e2))))
    return pts


def fit_circle(pts):
    """Kåsa 代數圓擬合。回傳 (cx, cy, r, 平均殘差)。"""
    n = len(pts)
    if n < 3:
        return None
    Sx = Sy = Sxx = Syy = Sxy = Sxz = Syz = Sz = 0.0
    for x, y in pts:
        z = x * x + y * y
        Sx += x; Sy += y; Sz += z
        Sxx += x * x; Syy += y * y; Sxy += x * y
        Sxz += x * z; Syz += y * z
    m = [[2*Sxx, 2*Sxy, Sx], [2*Sxy, 2*Syy, Sy], [2*Sx, 2*Sy, float(n)]]
    rhs = [Sxz, Syz, Sz]
    def det(a):
        return (a[0][0]*(a[1][1]*a[2][2]-a[1][2]*a[2][1])
              - a[0][1]*(a[1][0]*a[2][2]-a[1][2]*a[2][0])
              + a[0][2]*(a[1][0]*a[2][1]-a[1][1]*a[2][0]))
    base = det(m)
    if abs(base) < 1e-12:
        return None
    sol = []
    for c in range(3):
        mm = [row[:] for row in m]
        for r in range(3):
            mm[r][c] = rhs[r]
        sol.append(det(mm) / base)
    a, b, c = sol
    rsq = c + a*a + b*b
    if rsq <= 0:
        return None
    r = math.sqrt(rsq)
    resid = sum(abs(math.hypot(x - a, y - b) - r) for x, y in pts) / n
    return a, b, r, resid


def unwrap_total(pts, cx=0.0, cy=0.0):
    """解捲總轉角（度，絕對值）。"""
    total, prev = 0.0, None
    for x, y in pts:
        ang = math.atan2(y - cy, x - cx)
        if prev is not None:
            d = ang - prev
            while d > math.pi:  d -= 2 * math.pi
            while d < -math.pi: d += 2 * math.pi
            total += d
        prev = ang
    return abs(math.degrees(total))


def analyse(label, pts, gyro_total, stopwatch_k):
    print(f"\n{'='*66}\n{label}   （{len(pts)} 點）\n{'='*66}")
    if len(pts) < 32:
        print("  點數不足")
        return

    radii = [math.hypot(x, y) for x, y in pts]
    print(f"  水平分量 min/max      {min(radii):8.2f} / {max(radii):.2f} µT")
    print(f"  直接解捲總轉角        {unwrap_total(pts):8.0f}°"
          f"   （陀螺儀 {gyro_total:.0f}°）")

    fit = fit_circle(pts)
    if not fit:
        print("  圓擬合失敗")
        return
    cx, cy, r, resid = fit
    offset = math.hypot(cx, cy)
    print(f"  全段圓擬合            半徑 {r:.2f}  圓心偏移 {offset:.2f}"
          f"  殘差 {resid:.2f} µT ({resid/r*100:.1f}% of R)")

    total = unwrap_total(pts, cx, cy)
    print(f"  扣掉圓心後總轉角      {total:8.0f}°")
    if gyro_total > 0:
        k = total / gyro_total
        print(f"  倍率 k                {k:8.5f}"
              f"   （碼錶真值 {stopwatch_k}，差 {(k/stopwatch_k-1)*100:+.2f}%）")

    # 分段擬合 —— 圓心會不會漂移是最關鍵的診斷。
    print("\n  分段圓擬合（圓心固定的話，各段應該一致）")
    print(f"  {'段':>4} {'半徑':>8} {'圓心x':>9} {'圓心y':>9} {'偏移':>8} {'殘差':>8}")
    n_seg = 10
    step = len(pts) // n_seg
    centres = []
    for i in range(n_seg):
        seg = pts[i * step:(i + 1) * step]
        f = fit_circle(seg)
        if not f:
            continue
        sx, sy, sr, sres = f
        centres.append((sx, sy))
        print(f"  {i+1:>4} {sr:8.2f} {sx:9.2f} {sy:9.2f}"
              f" {math.hypot(sx, sy):8.2f} {sres:8.2f}")
    if len(centres) >= 2:
        spread = max(math.hypot(a - b, c - d)
                     for a, c in centres for b, d in centres)
        print(f"\n  圓心散佈範圍          {spread:.2f} µT")
        print("  → " + ("圓心穩定，扣掉就能用。" if spread < r * 0.15 else
                        "圓心在漂移，這個來源的讀數在量測過程中被改動了。"))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    d, cols, samples = load(sys.argv[1])
    summary = d.get("summary", {})
    gyro_total = summary.get("gyroTotalDegrees", 0.0)
    stopwatch_k = summary.get("stopwatchReferenceK", 0.99915)
    # meanRPM 是 App 已經套過 appliedFactor 的讀數，rawMeanRPM 才是未修正的。
    # 舊版直接把 meanRPM 再乘一次 k —— 沒存校準時剛好無害，存了就重複套用。
    applied = summary.get("appliedFactor")
    raw_rpm = summary.get("rawMeanRPM", summary.get("meanRPM", 0.0))

    print(f"錄製時間  {d.get('recordedAt')}")
    print(f"樣本數    {len(samples)}    時長 {summary.get('elapsedSeconds', 0):.1f} s"
          f"    取樣率 {summary.get('effectiveSampleRate', 0):.1f} Hz")
    print(f"平均轉速  {summary.get('meanRPM', 0):.4f} RPM"
          f"   偏差 {summary.get('errorPercent', 0):+.3f}%"
          + (f"   （已套用 k={applied:.5f}）" if applied else "   （未校準）"))
    print(f"未修正讀數 {raw_rpm:.4f} RPM")
    print(f"圈數      {summary.get('revolutions', 0)}"
          f"    陀螺儀總轉角 {gyro_total:.0f}°"
          f"    磁北總轉角 {summary.get('magneticTotalDegrees', 0):.0f}°")
    print(f"磁力計校準 {summary.get('fieldAccuracy', '—')}")
    # 標稱轉速辨識失敗時不要拿 1 當分母 —— 那會印出一個 −98% 的假偏差。
    nominal = summary.get("nominalRPM")
    corrected = raw_rpm * stopwatch_k
    line = f"\n改套碼錶 k={stopwatch_k} → {corrected:.4f} RPM"
    if nominal:
        line += f"   偏差 {(corrected / nominal - 1) * 100:+.3f}%"
    else:
        line += "   （標稱轉速辨識失敗，算不出偏差）"
    print(line)

    analyse("CoreMotion 已校準磁場（CMDeviceMotion.magneticField）",
            project(samples, cols, "b"), gyro_total, stopwatch_k)
    analyse("未校準原始磁力計（CMMagnetometerData）",
            project(samples, cols, "r"), gyro_total, stopwatch_k)


if __name__ == "__main__":
    main()
