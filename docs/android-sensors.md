# Android sensor behaviour

Notes measured on real hardware while porting the app to Android. Referenced from the
[README](../README.md); kept here because it is deeper than a README should go.

## Measured sampling behaviour

Android's `SensorManager` sampling rate is only a *hint* — the real rate is decided by the
vendor's HAL. Measured on a **Sony Xperia XZ Premium** (G8142, Android 9,
STMicroelectronics LSM6DSM gyroscope), phone stationary:

| Requested | Actual | Median interval | σ | Jitter | Long gaps | Worst gap | Sensor clock ÷ wall clock |
|---|---|---|---|---|---|---|---|
| 50 Hz | 53.96 Hz | 18.524 ms | 0.014 ms | 0.075% | 0 | 1.01× | 0.99995 |
| **100 Hz** | **107.92 Hz** | **9.277 ms** | **0.015 ms** | **0.160%** | **0** | **1.00×** | **0.99995** |
| 200 Hz | 215.74 Hz | 4.639 ms | 0.076 ms | 1.641% | 4 | 1.56× | 1.00051 |
| iPhone 15 Pro Max @100 Hz | 100.13 Hz | 9.990 ms | 0.005 ms | 0.05% | 0 | 1.00× | — |

**The consistent +7.9% is two effects stacked, not noise.** The LSM6DSM's ODR ladder is
12.5/26/52/104/208 Hz — *not* 50/100/200. The HAL rounds up to the ladder, then a fixed
oscillator deviation multiplies on top:

```
53.96 ÷ 52 = 1.03769      107.92 ÷ 104 = 1.03769      215.74 ÷ 208 = 1.03721
```

The first two agree to five decimal places.

**What matters is whether the *timestamps* are honest**, not whether the rate matches the
request. If the timestamp clock ran 7.9% fast, every frequency-domain result would shift by
the same amount and the "which part is at fault" diagnosis — the most valuable thing this
app does — would be wrong. Mean speed looks identical either way (ω is a physical quantity,
independent of the sampling clock), so speed alone cannot tell you. Comparing against
`SystemClock.elapsedRealtimeNanos()` settles it: the ratio is **0.99995**, so the
timestamps are honest and the app is unaffected — because it always integrates with real
timestamps and never assumes a fixed rate.

200 Hz degrades clearly (jitter 1.641%, four long gaps, worst gap 1.56×) and this app does
not need it — above 50 Hz only 0.72% of the weighted energy remains. So it uses the 100 Hz
setting, which also sidesteps Android 12's `HIGH_SAMPLING_RATE_SENSORS` permission entirely.


## Not every Android phone has a gyroscope

This is a constraint the iOS version never had to state — every iPhone has one. On Android,
mid-range devices often omit it. Measured on a **Sony Xperia XA2 Ultra** (H4233): a BMA255
accelerometer and an AK09916C magnetometer, and that's it. `Gravity`, `Linear Acceleration`
and `Rotation Vector` are all *virtual* sensors Qualcomm derives from those two — there is
no real angular-rate source, so the app cannot measure on that phone at all and says so
plainly on launch.

Deriving rotation from the magnetometer's heading instead is not a workaround: the iOS side
proved that path is swamped by once-per-revolution spatial field distortion (see
[`CLAUDE.md`](CLAUDE.md) pitfalls 13–15), and that device's magnetometer tops out at 50 Hz.

Check before you install: any spec sheet listing "gyroscope", or an app like *Sensor Box*.


---

# Android 的感測器行為

移植到 Android 時在真實硬體上量到的東西。從 [README](../README.zh-TW.md) 連過來，
放在這裡是因為它比 README 該有的深度更深。

## 實測的取樣行為

Android 的 `SensorManager` 取樣率設定只是**建議值**，實際頻率由廠商的 HAL 決定。
實測 **Sony Xperia XZ Premium**（G8142、Android 9、STMicroelectronics LSM6DSM 陀螺儀），
手機靜止：

| 要求 | 實際 | 間隔中位數 | σ | 抖動比 | 長空隙 | 最糟空隙 | 感測器時鐘 ÷ 牆鐘 |
|---|---|---|---|---|---|---|---|
| 50 Hz | 53.96 Hz | 18.524 ms | 0.014 ms | 0.075% | 0 | 1.01× | 0.99995 |
| **100 Hz** | **107.92 Hz** | **9.277 ms** | **0.015 ms** | **0.160%** | **0** | **1.00×** | **0.99995** |
| 200 Hz | 215.74 Hz | 4.639 ms | 0.076 ms | 1.641% | 4 | 1.56× | 1.00051 |
| iPhone 15 Pro Max @100 Hz | 100.13 Hz | 9.990 ms | 0.005 ms | 0.05% | 0 | 1.00× | — |

**那個一致的 +7.9% 是兩層疊出來的，不是雜訊。** LSM6DSM 的 ODR 階梯是
12.5/26/52/104/208 Hz —— **不是** 50/100/200。HAL 先進位到階梯上，再乘一個固定的
振盪器偏差：

```
53.96 ÷ 52 = 1.03769      107.92 ÷ 104 = 1.03769      215.74 ÷ 208 = 1.03721
```

前兩個到小數第五位完全相同。

**真正要緊的是「時間戳誠不誠實」**，不是實際速率符不符合要求。如果時間戳的時鐘快 7.9%，
所有頻域結果都會偏移同樣的量，「問題出在哪」那一區的判讀就全錯 —— 而那是這個 app
最有價值的功能。平均轉速兩種情況看起來一模一樣（ω 是物理量，與取樣時鐘無關），
所以光看轉速查不出來。拿 `SystemClock.elapsedRealtimeNanos()` 對照就分得開：
比值 **0.99995**，時間戳誠實，這個 app 不受影響 —— 因為它一律用真實時間戳積分，
從不假設固定速率。

200 Hz 明顯變差（抖動比 1.641%、4 次長空隙、最糟空隙 1.56×），而這個 app 不需要那麼快
—— 50 Hz 以上只剩 0.72% 的加權能量。所以固定用 100 Hz 檔，也順便完全避開 Android 12 的
`HIGH_SAMPLING_RATE_SENSORS` 權限。


## 不是每一支 Android 手機都有陀螺儀

這是 iOS 版從來不必交代的限制 —— 每一支 iPhone 都有。Android 的中階機常常省掉。
實測 **Sony Xperia XA2 Ultra**（H4233）：只有 BMA255 加速度計與 AK09916C 磁力計。
`Gravity`、`Linear Acceleration`、`Rotation Vector` 全部都是 Qualcomm 從那兩者算出來的
**虛擬**感測器 —— 沒有真的角速度來源，這個 app 在那支手機上完全跑不了，
開啟時會直接說明。

用磁力計的方位角微分來代替不是辦法：iOS 端證實那條路會被每圈一次的空間磁場失真蓋掉
（見 [`CLAUDE.md`](CLAUDE.md) 坑 13–15），而且那支的磁力計上限只有 50 Hz。

安裝前先確認：規格表有沒有列「陀螺儀」，或用 *Sensor Box* 之類的 app 查。

