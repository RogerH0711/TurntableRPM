# Android release 建置

這份是「怎麼把 debug APK 變成可以發布的 release APK」的步驟。

**金鑰與密碼永遠不進版控，也不該讓任何工具替你產生。**
下面第 1 步要你自己在終端機跑，因為它會問你設定密碼。

---

## 1. 產生上架金鑰（只做一次）

```bash
keytool -genkeypair -v \
  -keystore ~/turntablerpm-release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias turntablerpm
```

會問你幾件事：

| 問題 | 建議 |
|---|---|
| Enter keystore password | 自己設一組，**記到密碼管理員裡** |
| Re-enter new password | 同上 |
| 名字與姓氏 / 組織單位 / 組織 / 城市 / 州 / 國碼 | 隨意填，但要填得出來（會嵌進憑證裡） |
| Enter key password for `<turntablerpm>` | 直接按 Enter 沿用 keystore 密碼最省事 |

**`-validity 10000` 是刻意的**（約 27 年）。Google Play 要求上架金鑰的有效期
至少到 2033 年，而且**金鑰一旦用來上架就換不掉** —— 弄丟等於這個 app 再也
無法發布更新，只能用新的套件名稱重新上架。

備份 `~/turntablerpm-release.jks`：放進密碼管理員的附件、或另一台機器。
不要只留在這台電腦上，也不要放進這個 repo（`.gitignore` 已經擋掉 `*.jks`）。

---

## 2. 告訴建置系統金鑰在哪

在 `android/keystore.properties` 建一個檔（**已在 `.gitignore` 裡**）：

```properties
storeFile=../turntablerpm-release.jks
storePassword=你剛才設的密碼
keyAlias=turntablerpm
keyPassword=你剛才設的密碼
```

`storeFile` 的相對路徑是相對於 `android/`。想放到 repo 外面就用絕對路徑。

沒有這個檔案時建置**不會失敗**，只是產出未簽章的 APK ——
別人 clone 下來要能建置，不該因為缺一份他不可能有的金鑰而卡住。

---

## 3. 建置

```bash
make android-release
```

輸出在 `android/app/build/outputs/apk/release/app-release.apk`。

要上架 Google Play 的話用 AAB（Play 會依裝置切分，下載量更小）：

```bash
cd android && ./gradlew :app:bundleRelease
```

輸出在 `android/app/build/outputs/bundle/release/app-release.aab`。

---

## 4. 發布之前一定要做的

- [ ] **實機跑一遍縮混後的版本**。R8 是開著的（23.1 MB → 2.7 MB），
      而縮混壞掉只會在執行期出現。每個畫面都點過一次。
- [ ] `versionCode` 加一（`app/build.gradle.kts`）。Play 不接受重複的 versionCode。
- [ ] `versionName` 改成這一版的版本號。

## 還沒做的上架素材

- 付費開發者帳號（Google Play 一次性 25 美元）
- 截圖：**必須用實機拍**，模擬器沒有陀螺儀，畫面永遠是空狀態
- 商店描述、隱私權政策連結
- 資料安全問卷：這個 app 不收集任何資料、不連網，全部勾「不收集」
