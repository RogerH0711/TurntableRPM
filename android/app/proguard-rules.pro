# R8 規則。
#
# **這個 app 幾乎不需要額外規則** —— 沒有反射、沒有 JNI、沒有序列化框架。
# 匯出的 JSON 是手寫的字串拼接（`MeasurementExport`），存檔走 org.json 的
# 明確 put/get，兩者都不靠欄位名稱在執行期被反射到。
#
# Compose、kotlinx.coroutines、AndroidX 各自帶了 consumer rules，AGP 會自動套用。

# 例外的行號。沒有這個，release 版的當機報告只會有一堆 unknown source。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
