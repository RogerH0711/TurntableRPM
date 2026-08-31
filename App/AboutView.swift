import SwiftUI

/// 說明頁。
///
/// 這一頁的存在理由寫在 CLAUDE.md 的設計原則裡：**量的是「盤」，不是「音樂」**。
/// 唱片中心孔偏心造成的 wow 這個方法完全看不到，而那在實務上經常是最大宗 ——
/// 不講清楚，使用者會以為「App 說 0.05% 所以我的唱盤很好」，然後聽到抖動時
/// 不知道該怪誰。誠實交代限制比多一個功能重要。
struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("怎麼量的") {
                    Text("""
                    手機放在轉動的盤面上，用陀螺儀量自轉角速度。三軸角速度會投影到重力方向，\
                    所以手機擺得歪一點也不影響讀數 —— 傾斜 5° 若只讀單軸就會低估 0.38%，\
                    已經超過目標精度。

                    取樣率 100 Hz（iOS 對第三方 App 的上限），時間戳用感測器自己的時鐘，\
                    不假設每筆間隔相等。
                    """)
                    .font(.body)
                }

                Section("這個方法看不到什麼") {
                    Label {
                        Text("**唱片本身的偏心**").font(.body)
                    } icon: {
                        Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                    }
                    Text("""
                    手機是跟著盤面一起轉的，量到的是**盤**的轉速。唱片中心孔沒對準所造成的音高\
                    起伏，這個方法完全偵測不到 —— 而那在實務上經常是你聽到的抖動裡最大的一項。

                    換句話說：這裡讀到很漂亮的數字，不保證放起來就不抖。它能告訴你的是\
                    「唱盤本身好不好」，不是「這張唱片放起來好不好」。
                    """)
                    .font(.body)

                    Text("""
                    另外，抖晃率（W&F）的「最大偏差」這種數字一定要連同平滑視窗一起看，\
                    否則不同工具之間無法比較。這個 App 報的是 IEC 386 / DIN 45507 加權的 WRMS。
                    """)
                    .font(.body)

                    Text("""
                    **手機本身也會影響量測結果。** 它偏在盤面一側，會在軸承產生側向負載 —— \
                    實測會讓轉速慢約 0.3%，每圈一次的抖動大三成。所以量到的「偏心」有一部分\
                    是手機造成的，不是唱盤的問題。對面放個等重的東西配平就能大幅改善。
                    """)
                    .font(.body)
                }

                Section("關於校準") {
                    Text("""
                    陀螺儀可能有固定比例的讀數誤差，它是乘性的、量再久也平均不掉，\
                    只能靠外部參考校掉。這個 App 用碼錶：盤面貼個記號，數 100 圈計時，\
                    算出真實轉速再跟 App 的讀數比。

                    **沒有校準之前，「偏差 %」不能拿來調唱盤。** 你看到的偏差是\
                    「唱盤誤差」和「陀螺儀誤差」相乘的結果，分不開。

                    校準結果綁定在這一台裝置上（不同手機的陀螺儀不一樣），換手機要重做。
                    """)
                    .font(.body)

                    Text("""
                    參考值：開發用的這支 iPhone 15 Pro Max 校準倍率是 0.99915，\
                    也就是陀螺儀本身準到 0.085% —— 幾乎不需要修正。你的裝置可能不同。
                    """)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }

                Section("安全") {
                    safetyRow("magnet", "拿掉磁吸配件",
                              "MagSafe 配件、含磁鐵的手機殼都要拿掉。磁鐵靠近 MC 唱頭可能造成永久損傷，"
                              + "而且會干擾磁力計的診斷功能。Apple 原廠矽膠殼也內含磁鐵環。")
                    safetyRow("hand.raised", "唱臂鎖好",
                              "把唱臂鎖在臂座上，不要讓唱頭懸在盤面上方。手機在盤上時碰到唱針，"
                              + "壞的是唱針。")
                    safetyRow("record.circle", "用轉盤原本的墊子",
                              "絨布墊、不織布墊、橡膠墊都可以，不需要另外放一張唱片。"
                              + "重點是不要讓手機直接壓在裸露的盤面上 —— 兩邊都會刮。"
                              + "放之前確認手機背面和墊子上沒有沙粒。")
                    safetyRow("arrow.clockwise", "78 轉時放靠近中心",
                              "偏心擺放的離心力在 78 轉時是 33 轉的 5.5 倍。")
                }

                Section("手機怎麼擺") {
                    PlacementGuide()
                        .padding(.vertical, 4)
                }

                Section("怎麼量得準") {
                    Text("""
                    • 手機對面放一個等重的東西配平 —— 這是影響最大的一項，見上面的擺法
                    • 唱盤放水平，手機也放水平（盤面若傾斜，重力會每圈一次地干擾讀數）
                    • 至少量 90 秒；頻譜的解析度是 1／量測時長
                    • 想要準確的譜峰振幅就量 3 分鐘 —— 1 分鐘的量測會低估約 8%，
                      因為解析度不夠細，峰值落在兩個頻率格之間
                    • 校準時碼錶跟 App 要同步 —— 同一段轉動才算得準
                    • 量測中不要碰唱盤或桌子
                    """)
                    .font(.body)
                }

                Section {
                    Text("量測資料只留在這台裝置上，不會上傳到任何地方。"
                         + "「匯出原始資料」產生的檔案由你自己決定要不要分享。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("說明")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }

    private func safetyRow(_ icon: String, _ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(title, systemImage: icon)
                .font(.body.weight(.medium))
            Text(detail)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }
}

#Preview {
    AboutView()
}
