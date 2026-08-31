# 把 App 裝進 iPhone 的完整步驟

TurntableRPM M1 · 給第一次做 iOS App 的人 · 2026 年 8 月 30 日

## 開始之前

整段流程大約 20–40 分鐘，大部分時間在等 Xcode。**每一步都會告訴你「成功長什麼樣」**，看到不一樣的東西就往[第九節](#troubleshooting)查。

需要準備：接得上網路的 Mac（Xcode 已裝好）、iPhone、一條能傳資料的線（有些充電線只能充電）、你的 Apple ID 帳號密碼。

**先建立一個觀念，後面會少很多困惑。**

iPhone 不讓隨便什麼程式裝進去。要裝，程式必須被「簽章」——用一組跟你 Apple ID 綁定的憑證蓋章，iPhone 才願意執行。

用免費的 Apple ID 也可以簽，但有兩個限制：**簽章 7 天後過期**（App 就打不開，重新裝一次即可），同時**最多 3 個自簽的 App**。付費的 Apple Developer Program（每年 99 美元）簽章效期一年。

自用工具先用免費的就好。後面第八節會說 7 天到了怎麼辦。

## 第一節：安裝 XcodeGen

Xcode 的專案檔（`.xcodeproj`）是一個內容很亂的檔案，兩台機器同步時特別容易打架。XcodeGen 讓我們改用一份好讀的 `project.yml`，需要時再生成專案檔——所以專案檔不進版控，也就不會衝突。

**步驟 1.1** 開啟「終端機」（Terminal）。用 Spotlight 搜尋「終端機」就找得到。

**步驟 1.2** 先確認有沒有 Homebrew：

```sh
brew --version
```

看到版本號（例如 `Homebrew 4.x.x`）就往下走。若顯示 `command not found`，先裝 Homebrew：

```sh
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

裝完照畫面最後幾行的指示，把 brew 加進 PATH，然後**關掉終端機再重開**。

**步驟 1.3** 安裝 XcodeGen：

```sh
brew install xcodegen
```

**步驟 1.4** 確認：

```sh
xcodegen --version
```

**成功的樣子**：印出版本號，例如 `Version: 2.43.0`。

## 第二節：產生 Xcode 專案

**步驟 2.1** 進到專案資料夾，跑一次環境自我檢查：

```sh
cd ~/TurntableRPM
make doctor
```

這會列出 Xcode 路徑、Swift 版本、XcodeGen 版本、有沒有偵測到 iPhone。現在 iPhone 那項顯示「沒偵測到」是正常的，還沒接線。

**步驟 2.2** 產生專案：

```sh
make setup
```

**成功的樣子**：最後幾行印出 `Created project at ...TurntableRPM.xcodeproj`，接著是一份「接下來要做什麼」的提示。

這一步同時做了兩件事：從 `project.yml` 生出 `TurntableRPM.xcodeproj`，以及把 `Config/Local.xcconfig.example` 複製成 `Config/Local.xcconfig`（放你的簽章設定，這個檔案不會被推上 GitHub）。

## 第三節：在 Xcode 裡設定簽章

**步驟 3.1** 開啟專案：

```sh
make open
```

Xcode 會啟動。第一次開啟可能會問要不要下載額外元件，讓它裝完。

**步驟 3.2** 左上角有一個檔案總管樣式的側邊欄，最上面那一列藍色圖示寫著 **TurntableRPM**，點它。

**步驟 3.3** 中間會出現設定畫面。左側有兩區：**PROJECT** 和 **TARGETS**。點 **TARGETS** 底下的 **TurntableRPM**。

**步驟 3.4** 上方那排分頁（General、Signing & Capabilities、Resource Tags…）點 **Signing & Capabilities**。

**步驟 3.5** 確認 **Automatically manage signing** 已經打勾。

**步驟 3.6** **Team** 是一個下拉選單，現在應該顯示 `None`。點開它：

- 如果已經看到你的名字（例如 `Roger Yang (Personal Team)`），直接選它，跳到步驟 3.8。
- 如果沒有，選 **Add an Account…**，輸入 Apple ID 與密碼登入。登入後關掉那個視窗，回到 Team 下拉選單，這時就會出現 `你的名字 (Personal Team)`，選它。

**步驟 3.7** 選好之後，Xcode 會花幾秒鐘產生憑證。

**成功的樣子**：Team 底下出現一行小字 `Provisioning Profile: Xcode Managed Profile`，而且**沒有紅色錯誤訊息**。

若出現紅字 `Failed to register bundle identifier`，代表 `com.roger.turntablerpm` 這個名字已經被別人用掉了。解法在[第九節](#troubleshooting)。

**步驟 3.8** 現在把 Team ID 存起來，這樣以後重新產生專案就不用再選一次。回到終端機：

```sh
make teamid
```

它會印出類似：

```
你的 Team ID：A1B2C3D4E5
請把 Config/Local.xcconfig 裡的那一行改成：DEVELOPMENT_TEAM = A1B2C3D4E5
```

**步驟 3.9** 照著做。用任何編輯器打開 `~/TurntableRPM/Config/Local.xcconfig`，把最後那行 `DEVELOPMENT_TEAM =` 補上你的 ID，存檔。

**為什麼要多這一步？**

`make generate` 會把 `.xcodeproj` 整個重新生成，你在 Xcode 裡點選的 Team 會被覆蓋掉。放進 `Local.xcconfig` 之後，每次重新生成都會自動帶上。這個檔案在 `.gitignore` 裡，不會被推到 GitHub——你的 Team ID 沒必要公開。

## 第四節：把 iPhone 變成開發裝置

**步驟 4.1** 用線把 iPhone 接上 Mac。

**步驟 4.2** iPhone 上會跳出「**是否信任這台電腦？**」，選 **信任**，然後輸入 iPhone 的解鎖密碼。

**步驟 4.3** 回終端機確認 Mac 看得到手機：

```sh
make doctor
```

「已連接的 iPhone」那一段應該列出你的手機。

**步驟 4.4** 在 iPhone 上開啟開發者模式：**設定 ▸ 隱私權與安全性**，往下滑到最底，找 **開發者模式**。

**找不到「開發者模式」是正常的。** 這個選項只在 iPhone 被 Xcode 嘗試安裝過一次之後才會出現。

如果現在沒有，先跳到[第五節](#run)按一次 ⌘R（會失敗，沒關係），再回來這一步就找得到了。

**步驟 4.5** 把開發者模式打開，iPhone 會要求**重新開機**。重開之後解鎖，畫面會再問一次要不要啟用，選 **開啟**，再輸入一次密碼。

## 第五節：第一次裝到手機上

**步驟 5.1** 回到 Xcode。視窗最上方中央有一個裝置選擇器，現在可能寫著 `iPhone 15 Pro`（那是模擬器）。點開它，在清單上方的 **iOS Device** 區塊選你**實體的 iPhone**。

**一定要選實體 iPhone，不能用模擬器。**

模擬器沒有陀螺儀，這個 App 在模擬器上永遠讀不到任何數字。App 偵測到之後會直接顯示一個橘色警告。

**步驟 5.2** 按 **⌘R**（或左上角的三角形播放鍵）。

Xcode 開始編譯。第一次會比較久，因為要連 `TurntableCore` 這個套件一起編。狀態列會依序顯示 Building、Signing、Installing。

**步驟 5.3** 第一次執行大機率會停在這個錯誤：

> Could not launch "TurntableRPM" — 不受信任的開發者

這是正常的。iPhone 認得這個 App，但還不信任簽它的憑證。

**步驟 5.4** 在 iPhone 上：**設定 ▸ 一般 ▸ VPN與裝置管理**。在「開發者 App」底下會看到你的 Apple ID，點進去，選 **信任「你的 Apple ID」**，再確認一次。

**步驟 5.5** 回 Xcode，再按一次 **⌘R**。

**成功的樣子**：iPhone 上跳出名為 **RPM** 的 App，畫面中央一個大大的 `—`，下面寫著 `RPM`，再下面是「感測器診斷」區塊，還有一顆藍色的「開始量測」按鈕。

## 第六節：第一次量測

先在桌上測，確認基本功能，再碰唱盤。

**步驟 6.1** 按 **開始量測**。若跳出動作權限的請求，選允許（有些情況不會跳，那也正常）。

**步驟 6.2** 把手機拿在手上，慢慢水平轉一圈。

**成功的樣子**：中央的數字跟著動；診斷區的「瞬時角速度」有數值在變；「樣本數」持續增加；「實際取樣率」穩定在 100 Hz 附近。

如果「磁北 yaw」顯示「不可用」，記下來告訴我——這會影響 M3 自動校準的做法。

**步驟 6.3** 按 **停止**。

### 上唱盤前的安全檢查

1. **拿掉磁吸手機殼、MagSafe 配件、任何含磁鐵的東西。** 磁鐵靠近 MC 唱頭可能造成永久損傷。
2. **唱臂鎖在臂座上**，不要讓唱頭懸在盤面上方。
3. **墊上唱片或原廠墊再放手機**，並確認手機殼底部沒有沙粒——會刮傷盤面。
4. 手機放**靠近中心**，但不必正對中軸。

**步驟 6.4** 唱盤設 33⅓ 轉，啟動，等轉速穩定。

**步驟 6.5** 手機放上去，按 **開始量測**，讓它跑至少 60 秒。

**步驟 6.6** 按停止之前，把畫面拍下來或記下這幾個數字：

| 要看什麼 | 為什麼 |
| --- | --- |
| 平均轉速（RPM） | 這是主角。應該落在 33.3 附近 |
| 有沒有辨識出「33⅓ 轉」 | 驗證標稱辨識 |
| 實際取樣率 | 貼近 100 Hz 才正常；掉太多代表回呼被塞住 |
| 磁北 yaw 可不可用 | 決定 M3 校準怎麼做 |
| 累積圈數 | 60 秒 33⅓ 轉應該是 33 圈左右 |

**步驟 6.7** **這一步特別重要**：停止，把手機**轉個角度、換個位置**（還是靠近中心），再測一次 60 秒。

兩次的平均轉速應該幾乎一樣。這是在驗證「重力投影法」——程式把三軸角速度投影到重力方向來取出自轉分量，所以手機怎麼擺都不該影響讀數。如果換位置數字就跳，那是真的有問題，告訴我。

**現在還不能拿這個數字去調唱盤。**

沒校準之前，iPhone 陀螺儀的比例因子誤差可能有 0.5%–3%——比你想量的唱盤誤差還大一個數量級。所以那個「\+0.xxx%」現在只是參考。

M1 要驗證的是「數字合不合理、穩不穩定、換位置會不會變」，**不是準不準**。校準是 M3 的事，做完才談得上 0.1%。

## 第七節：程式改了之後怎麼重跑

日常只有三種情況：

| 情況 | 做什麼 |
| --- | --- |
| 只改了 `.swift` 檔的內容 | 在 Xcode 直接按 ⌘R |
| **新增或刪除**了 `App/` 底下的檔案 | 先 `make generate`，再回 Xcode 按 ⌘R |
| 只想跑演算法測試 | `make test`（不需要手機，約 1 秒） |

第二種情況要特別記住：XcodeGen 是靠**掃描資料夾**決定專案包含哪些檔案的，新檔案不重新生成就不會被編進去。

## 第八節：簽章 7 天過期了

有一天你打開 App，它閃一下就關掉，或是根本點不開——那就是簽章過期了。

**解法**：把 iPhone 接上 Mac，Xcode 按 ⌘R 重裝一次。不用重新設定任何東西，30 秒的事。

覺得每週一次太煩的話，付費的 Apple Developer Program（每年 99 美元）簽章效期是一年。以自用工具來說，先撐一陣子再決定。

## 第九節：卡住的時候

| 症狀 | 意思 | 怎麼修 |
| --- | --- | --- |
| `xcodegen: command not found` | XcodeGen 沒裝好 | `brew install xcodegen`；若 brew 也沒有，回[第一節](#xcodegen) |
| `no such module 'XCTest'` | 只裝了 Command Line Tools，沒有完整 Xcode | 裝完整 Xcode，再跑 `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` |
| Signing 紅字 `Failed to register bundle identifier` | `com.roger.turntablerpm` 被別人用了 | 編輯 `project.yml`，把 `PRODUCT_BUNDLE_IDENTIFIER` 改成別的（例如加上生日數字），存檔後 `make generate` |
| Signing 紅字 `No account for team` | Team 沒選好 | 回[步驟 3.6](#signing) |
| `Could not launch — 不受信任的開發者` | 憑證還沒信任 | [步驟 5.4](#run) |
| iPhone 找不到「開發者模式」 | 還沒被 Xcode 碰過 | 先按一次 ⌘R（會失敗），再回設定裡找 |
| 裝置選單裡看不到 iPhone | 線不對，或沒選信任 | 換一條能傳資料的線；iPhone 上重新選「信任這台電腦」 |
| App 顯示「讀不到動作感測器」 | 選到模擬器了 | 裝置選單改選實體 iPhone |
| 實際取樣率遠低於 100 Hz | 回呼被塞住 | 記下數字告訴我，這是要調的地方 |
| 完全不知道現在什麼狀況 | — | 跑 `make doctor`，把輸出貼給我 |

## 檢查清單

照順序打勾：

- [ ] `brew --version` 有版本號
- [ ] `xcodegen --version` 有版本號
- [ ] `make setup` 成功產生 `.xcodeproj`
- [ ] Xcode 的 Signing & Capabilities 沒有紅字
- [ ] `make teamid` 印出 ID，且已填進 `Config/Local.xcconfig`
- [ ] iPhone 接上線並選了「信任這台電腦」
- [ ] iPhone 的開發者模式已開啟（重開機過）
- [ ] Xcode 裝置選單選的是**實體 iPhone**，不是模擬器
- [ ] ⌘R 之後 iPhone 上出現名為 RPM 的 App
- [ ] iPhone 設定裡已信任你的開發者憑證
- [ ] 拿在手上轉，數字會動
- [ ] 安全檢查做完（磁吸配件已拿掉、唱臂已鎖、墊子已墊）
- [ ] 唱盤上量測 60 秒，記下五項數字
- [ ] 換位置再測一次，兩次結果一致

全部打完就把數字貼給我，我們進 M2。
