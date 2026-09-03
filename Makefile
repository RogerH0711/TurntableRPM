XCODEGEN ?= xcodegen
PACKAGE := Packages/TurntableCore

.PHONY: setup generate open teamid doctor test docker-test reference clean \
        android-test android-apk android-devices android-crosscheck

## 第一次設定：檢查 xcodegen、備好個人簽章設定、產生專案
setup:
	@command -v $(XCODEGEN) >/dev/null 2>&1 || { \
		echo "找不到 xcodegen，請先執行： brew install xcodegen"; exit 1; }
	@$(MAKE) generate
	@echo ""
	@echo "接下來："
	@echo "  1. make open"
	@echo "  2. Xcode ▸ 選 TurntableRPM target ▸ Signing & Capabilities ▸ Team 選你的 Apple ID"
	@echo "  3. 把那個 Team ID 填進 Config/Local.xcconfig，以後重新產生專案就不會被洗掉"
	@echo "  4. iPhone 接上線，Xcode 左上角選你的裝置，按 ⌘R"

## 產生 .xcodeproj（專案檔不進版控，隨時可重新產生）
generate:
	@[ -f Config/Local.xcconfig ] || cp Config/Local.xcconfig.example Config/Local.xcconfig
	$(XCODEGEN) generate

open: generate
	open TurntableRPM.xcodeproj

## 印出 Xcode 幫你設好的 Team ID，貼進 Config/Local.xcconfig
teamid:
	@id=$$(xcodebuild -project TurntableRPM.xcodeproj -target TurntableRPM \
		-showBuildSettings 2>/dev/null | awk '/ DEVELOPMENT_TEAM = /{print $$3}' | head -1); \
	if [ -n "$$id" ]; then \
		echo "你的 Team ID：$$id"; \
		echo "請把 Config/Local.xcconfig 裡的那一行改成：DEVELOPMENT_TEAM = $$id"; \
	else \
		echo "還讀不到 Team ID。"; \
		echo "請先 make open，在 Xcode 的 Signing & Capabilities 把 Team 選成你的 Apple ID，存檔後再跑一次。"; \
	fi

## 環境自我檢查
doctor:
	@echo "--- Xcode ---"; xcode-select -p 2>/dev/null || echo "  找不到 Xcode"
	@echo "--- swift ---"; swift --version 2>&1 | head -1 || true
	@echo "--- xcodegen ---"; xcodegen --version 2>/dev/null || echo "  未安裝：brew install xcodegen"
	@echo "--- 已連接的 iPhone ---"; \
		xcrun devicectl list devices 2>/dev/null | grep -i iphone || echo "  沒偵測到 iPhone（接上線並在手機上選「信任」）"
	@echo "--- Local.xcconfig ---"; \
		[ -f Config/Local.xcconfig ] && cat Config/Local.xcconfig | grep DEVELOPMENT_TEAM || echo "  還沒建立"

## 演算法測試（不需要模擬器也不需要實機）
test:
	$(MAKE) -C $(PACKAGE) test

docker-test:
	$(MAKE) -C $(PACKAGE) docker-test

reference:
	$(MAKE) -C $(PACKAGE) reference

# --- Android ---
# core 模組是純 Kotlin、不依賴 Android framework，所以測試在 JVM 上跑，
# 不需要手機也不需要模擬器 —— 跟上面的 `make test` 是同一個理由。
# 黃金值兩邊都讀 Packages/TurntableCore/Reference/golden.json。
ANDROID_JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home

## Kotlin 測試（JVM，不需要手機）—— 核心加上 app 層裡不依賴 Android 的部分
android-test:
	cd android && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew :core:test :app:testDebugUnitTest

## 重新產生 Android 的四語系 strings.xml（改 tools/strings_catalog.json 之後跑）
android-strings:
	cd android && python3 tools/gen_strings.py . tools/strings_catalog.json

## 建出 release APK（需要 android/keystore.properties，見 docs/android-release.md）
android-release:
	cd android && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew :app:assembleRelease
	@echo ""
	@ls -la android/app/build/outputs/apk/release/*.apk
	@echo ""
	@echo "檔名帶 -unsigned 代表沒有找到金鑰，見 docs/android-release.md"

## 建出 debug APK
android-apk:
	cd android && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew :app:assembleDebug
	@echo ""
	@echo "APK： android/app/build/outputs/apk/debug/app-debug.apk"

## 拿 iOS 匯出的真實資料驗證 Kotlin 核心：make android-crosscheck FILE=xxx.json
android-crosscheck:
	@[ -n "$(FILE)" ] || { echo "用法： make android-crosscheck FILE=TurntableRPM-....json"; exit 1; }
	cd android && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew -q :core:crossCheck -Pfile="$(PWD)/$(FILE)"

## 列出接上的 Android 裝置
android-devices:
	@$$HOME/Library/Android/sdk/platform-tools/adb devices -l

clean:
	rm -rf TurntableRPM.xcodeproj DerivedData
	$(MAKE) -C $(PACKAGE) clean
