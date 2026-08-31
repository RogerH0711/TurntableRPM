# TurntableRPM

[![Swift](https://github.com/RogerH0711/TurntableRPM/actions/workflows/swift.yml/badge.svg)](https://github.com/RogerH0711/TurntableRPM/actions/workflows/swift.yml)

用 iPhone 量測黑膠唱盤的轉速與抖晃率。

- `Packages/TurntableCore` — 演算法核心。純 Swift + Foundation，不碰 UIKit 也不碰 CoreMotion，
  所以能在 Mac 原生、Linux container、CI 上跑測試，不需要模擬器也不需要實機。
- `App` — iOS app。CoreMotion 的資料在這裡轉成 `SpinSample` 餵給演算法核心。

`.xcodeproj` 由 XcodeGen 從 `project.yml` 產生，**不進版控**——兩台機器同步時就不會有
專案檔的 merge 衝突（這是 `.xcodeproj` 出名的痛點）。

## 第一次設定

```sh
brew install xcodegen
cd ~/TurntableRPM
make setup
make open
```

在 Xcode 裡選 TurntableRPM target ▸ Signing & Capabilities ▸ Team 選你的 Apple ID。
選好之後把 Team ID 填進 `Config/Local.xcconfig`（這個檔案不進版控），
以後每次 `make generate` 重新產生專案都不會被洗掉。

iPhone 要先開啟開發者模式：設定 ▸ 隱私權與安全性 ▸ 開發者模式 ▸ 開啟 ▸ 重開機。
接上線之後 Xcode 左上角選你的裝置，⌘R。

免費 Apple ID 的簽章 7 天後過期，過期就重新 ⌘R 一次；付費開發者帳號是一年。

## 日常指令

| 指令 | 做什麼 |
|---|---|
| `make test` | 跑演算法測試（74 個，約 1 秒） |
| `make docker-test` | 在 Linux container 跑同一批測試（偶發卡死，見下） |
| `make generate` | 改過 `project.yml` 或新增檔案後重新產生 `.xcodeproj` |
| `make open` | 產生並開啟專案 |
| `make reference` | 跑 Python 參考實作，重新產生黃金向量 |

**新增或刪除 `App/` 底下的檔案之後要跑一次 `make generate`**，
XcodeGen 是靠掃資料夾決定 target 內容的。

## 現在做到哪

**M0 演算法核心（完成）** 74 個測試全過，黃金值由 `Packages/TurntableCore/Reference/`
的獨立 Python 實作算出。macOS 與 Linux container 都驗過。

**M1 感測器接通（完成）** `MotionEngine` 以 100 Hz 讀 `CMDeviceMotion`，
把三軸角速度投影到重力方向取出自轉分量。實機驗證：取樣率 100.1 Hz、
圈數計算正確、重力投影讓手機傾斜不影響讀數。

**M3 碼錶校準（完成）** 輸入碼錶量到的圈數與秒數，算出比例因子 k 並永久套用。
實測這支 iPhone 15 Pro Max 的 k = 0.99915 —— **陀螺儀本身準到 0.085%**，
三次碼錶交叉驗證全距 0.08%。

> 指南針自動校準試過兩條路都失敗，已降為診斷工具。原因見 `CLAUDE.md` 坑 11 與 15。

**接下來** M2 畫面重整、M4 頻譜／熱圖／歷史、M5 唱盤設定檔。

### `make docker-test` 會偶發卡死

Docker Desktop for Mac 的 VirtioFS 問題，症狀是 `Build complete!` 之後沒有任何測試輸出。
`docker ps` 看到 `swift test` 的 container 掛著就是卡了，`docker rm -f <名字>` 清掉。
**不要用管線包起來跑**（`| grep`），輸出會被整批緩衝，看不出是卡住還是在跑。
跨平台驗證交給 GitHub Actions 比較省事。

## 安全提醒

- 拿掉磁吸手機殼／MagSafe 配件，磁鐵靠近 MC 唱頭可能造成永久損傷。
- 唱臂鎖在臂座上，不要讓唱頭懸在盤面上方。
- 用轉盤原本的墊子（絨布／不織布／橡膠都可以，不必另外放唱片），
  不要讓手機直接壓在裸露的盤面上。放之前確認兩邊都沒有沙粒。
- 78 轉時把手機放靠近中心，偏心的離心力比 33 轉大 5.5 倍。
