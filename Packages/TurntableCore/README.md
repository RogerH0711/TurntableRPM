# TurntableCore

黑膠唱盤轉速與抖晃量測 App 的演算法核心（規格書 M0）。

純 Swift、不 import UIKit 也不 import CoreMotion，所以**可以在 Mac 上直接跑測試，不必先有 Xcode 專案、不必上機**。
之後 iOS app 只要把 CoreMotion 的資料餵成 `SpinSample` 就能用。

## 跑測試

三條路，任選一條。**這個 target 是純 Swift + Foundation，沒有 UIKit 也沒有 CoreMotion，
所以在 Linux container 裡也跑得動** —— 這正是把演算法切出來的用意。

### A. macOS 原生（日常開發用這個）

```sh
make test        # 等同 swift test
```

74 個測試約 1 秒跑完。需要完整 Xcode，只裝 Command Line Tools 會出現 `no such module 'XCTest'`。

### B. Docker container（驗證跨平台，或在 Windows 上工作）

```sh
make docker-test
```

**建置目錄一定要放在具名 volume，不能放在 bind mount 上。** Docker Desktop for Mac 在
Apple Silicon 上，從 VirtioFS 掛載點執行連結出來的二進位檔會偶發卡死：畫面停在
`Build complete!` 之後就沒有下文，而且因為管線輸出是塊狀緩衝，連跑到第幾個測試都看不到
（Ctrl-C 之後緩衝區才會吐出來）。把 `--scratch-path` 指到 container 自己的檔案系統就沒事，
順便也快很多。

`make docker-clean` 可以清掉那個 volume。VS Code 直接 Reopen in Container 也已經配好同樣的設定。

### C. 疑難排解：只裝了 Command Line Tools

```sh
swift test
```

只裝 Command Line Tools 會失敗，錯誤是 `no such module 'XCTest'` ——
CLT 的 Swift 工具鏈不含 XCTest。裝完 Xcode 後：

```sh
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
swift test
```

只要編譯不跑測試的話，CLT 就夠了：`swift build`。

### D. Xcode GUI

File ▸ Open ▸ 選 TurntableCore 目錄，⌘U。

### CI

`.github/workflows/swift.yml` 會在每次 push 時於 Linux 與 macOS 兩邊跑 `swift test`。
用不到 macOS runner 的話（比較耗額度）把 `macos` job 刪掉即可。

## 加進 iOS App 專案

Xcode ▸ File ▸ Add Package Dependencies ▸ Add Local… ▸ 選這個資料夾，
然後在 app target 的 Frameworks 加入 `TurntableCore`。

## 模組對照

| 檔案 | 規格章節 | 做什麼 |
|---|---|---|
| `Vector3` / `SpinSample` | §2 | 基礎型別 |
| `SpinProjector` | §2.2 | 三軸角速度投影到重力方向，消掉傾斜的 cos 誤差 |
| `TurntableSpeed` | §1 | 標稱轉速，RPM × 6 = °/s |
| `SpeedStatistics` | §3.2 | 梯形積分平均、穩定閘門、±8% 標稱辨識 |
| `DeviationSeries` | §3.3 | d(t) 與最大偏差（一定帶著頻寬回報） |
| `MovingAverage` | §3.3 | 顯示路徑平滑 + 頻率響應（證明為何不能用在分析路徑） |
| `UniformResampler` | §4.3 | 抖動時間戳 → 等間隔網格 |
| `FFT` | §3.5 | 純 Swift radix-2，含 Hann 窗振幅頻譜 |
| `WowFlutterWeighting` | §3.4 | IEC 386 / DIN 45507 加權曲線閉式近似 |
| `WowFlutterAnalyzer` | §3.4 | 頻域加權 → WRMS 與 DIN 2σ 峰值 |
| `PhaseIntegrator` | §3.6 | 混合式相位：磁北錨定每圈起點，圈內用陀螺儀 |
| `PolarAccumulator` | §3.6 | 72 格極座標分箱 |
| `ScaleCalibrator` | §3.7 | 指南針校準倍率、碼錶備援、所需圈數 |
| `LoadCompensator` | §3.8 | 手機重量兩點外插 |
| `SyntheticSignal` | §8.1 | 已知答案的合成訊號 |

## 測試怎麼驗的

所有黃金值由一份獨立的 Python 參考實作算出，不是拿 Swift 自己的輸出當標準答案。
測試涵蓋：

- 投影法在 0–30° 傾斜下讀數不變（對照組：只讀 z 軸在 5° 就錯 0.38%）
- 四種標稱轉速的平均值回推與 ±8% 辨識窗邊界
- 移動平均的零點頻率 —— 特別是 N=25 的零點正好殺死 4 Hz 加權峰值
- 加權曲線對 AES 四個錨點的誤差 ≤ 3.5%
- 純正弦注入的 WRMS = A × W(f) / √2（五組）
- 2σ/WRMS 比值：正弦 ≈1.41、高斯 ≈1.96
- 熱圖峰值落在正確角度（四種相位，誤差 < 一格）
- 校準能從 ±3% 的比例因子誤差回推到 0.1% 以內
- 磁北錨定把 60 秒的相位漂移從 120° 壓到 5° 以內
- FFT 對照直接定義的 DFT，誤差 < 1e-9

## 下一步（M1）

`MotionEngine`：包 `CMMotionManager`，用 `.xMagneticNorthZVertical`、100 Hz、專屬 OperationQueue，
把 `CMDeviceMotion` 轉成 `SpinSample` 餵給這裡。注意模擬器沒有陀螺儀，必須實機測試。

## Reference/

`Reference/reference.py` 是這套演算法的獨立 Python 參考實作，`verify.py` 跑完會印出所有驗證結果並產生
`golden.json`。Swift 測試裡的每個期望值都來自這裡 —— 這樣測試驗的是獨立算出的正解，
而不是 Swift 實作自己的輸出。

改演算法時的流程：先在 Python 改、跑 `python3 verify.py` 確認數學，再同步到 Swift、跑 `swift test`。

```sh
cd ~/code/TurntableCore/Reference
pip install numpy scipy
python3 verify.py
```
