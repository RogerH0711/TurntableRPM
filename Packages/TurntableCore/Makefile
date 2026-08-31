SWIFT_IMAGE ?= swift:5.10
BUILD_VOLUME ?= turntablecore-linux-build

.PHONY: build test docker-test docker-shell docker-clean reference clean

## macOS 原生（日常開發用這個，最快）
build:
	swift build

test:
	swift test

## Linux container（驗證跨平台，或在 Windows 上工作時）
##
## 建置目錄放在具名 volume，不放在 bind mount 上。
## Docker Desktop for Mac 從 VirtioFS 掛載點執行二進位檔會偶發卡死，
## 症狀是 "Build complete!" 之後沒有任何測試輸出（輸出是塊狀緩衝，所以連跑到哪都看不到）。
docker-test:
	docker run --rm \
		-v "$(CURDIR)":/pkg -w /pkg \
		-v $(BUILD_VOLUME):/build \
		$(SWIFT_IMAGE) swift test --scratch-path /build

docker-shell:
	docker run --rm -it \
		-v "$(CURDIR)":/pkg -w /pkg \
		-v $(BUILD_VOLUME):/build \
		$(SWIFT_IMAGE) bash

docker-clean:
	-docker volume rm $(BUILD_VOLUME)

## 跑 Python 參考實作，重新產生黃金向量
reference:
	cd Reference && python3 verify.py

clean:
	rm -rf .build .build-linux
