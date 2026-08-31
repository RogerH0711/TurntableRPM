import SwiftUI

/// 第一次開啟時的說明。
///
/// 刻意包含**限制**而不只是功能。這個工具量的是「盤」不是「音樂」，而且沒有
/// 校準之前偏差不能用 —— 這兩件事使用者一定要先知道，否則他會拿一個漂亮的
/// 數字得出錯誤的結論。誠實交代限制比多列幾個功能重要。
struct OnboardingView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var page = 0
    private let lastPage = 3

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                whatItDoes.tag(0)
                whatYouGet.tag(1)
                howToMeasure.tag(2)
                howToPlace.tag(3)
            }
            .tabViewStyle(.page)
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button(page == lastPage ? "開始使用" : "下一步") {
                if page == lastPage { dismiss() } else { withAnimation { page += 1 } }
            }
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .buttonStyle(.borderedProminent)
            .padding(.horizontal)
            .padding(.bottom, 8)

            Button("略過") { dismiss() }
                .font(.footnote)
                .foregroundStyle(.secondary)
                .padding(.bottom, 12)
                .opacity(page == lastPage ? 0 : 1)
        }
    }

    // MARK: - 頁面

    private var whatItDoes: some View {
        page(icon: "record.circle", title: "量黑膠唱盤的轉速") {
            bullet("用手機的陀螺儀，把手機放在轉動的盤面上就能量。不需要頻閃盤、"
                   + "不需要額外硬體。")
            bullet("目標精度 **0.1%**。三軸角速度會投影到重力方向，所以手機擺得"
                   + "歪一點也不影響讀數。")
            bullet("除了轉速，還會算**抖晃率（wow & flutter）**，並用頻譜告訴你"
                   + "問題出在哪個零件。")
            Divider().padding(.vertical, 6)
            note("**它看不到唱片本身的偏心。** 手機跟著盤轉，量到的是盤的轉速；"
                 + "唱片中心孔沒對準造成的音高起伏，這個方法偵測不到 —— 而那在實務上"
                 + "經常是你聽到的抖動裡最大的一項。")
        }
    }

    private var whatYouGet: some View {
        page(icon: "waveform.path.ecg", title: "你會得到什麼") {
            bullet("**平均轉速與偏差 %** —— 你的盤到底是快還是慢、差多少。")
            bullet("**抖晃率**（IEC 386 / DIN 45507 加權 WRMS），可以跟原廠規格直接比。")
            bullet("**譜峰判讀** —— 整數倍代表跟著盤面轉的東西（偏心、盤面變形）；"
                   + "非整數倍代表傳動鏈上轉速不同的零件（馬達、皮帶輪）。")
            bullet("**極座標熱圖** —— 誤差集中在盤面的哪一段。")
            bullet("**歷史記錄** —— 調整前後可以並排比較。")
            Divider().padding(.vertical, 6)
            note("**沒有校準之前，「偏差 %」不能拿來調唱盤。** 那個數字是"
                 + "唱盤誤差與陀螺儀誤差相乘的結果，兩者分不開。校準用碼錶做，"
                 + "一台裝置做一次就好。")
        }
    }

    private var howToMeasure: some View {
        page(icon: "list.number", title: "怎麼量") {
            step(1, "**先讓轉盤轉起來**，等它到達正常轉速。")
            step(2, "拿掉磁吸配件與含磁鐵的手機殼，唱臂鎖好，盤面墊一張唱片。")
            step(3, "手機平放在唱片上，長邊中點靠著轉軸。")
            step(4, "按下按鈕。**自動模式**會等轉速穩定才開始記錄，盤面停下時自己結束；"
                    + "**手動模式**由你自己按開始與停止。")
            step(5, "量 **90 秒以上**。時間愈長，頻譜的解析度愈好 —— 要分辨傳動鏈的"
                    + "特徵頻率需要至少 90 秒。")
            Divider().padding(.vertical, 6)
            note("量測中畫面會**反向旋轉**，讓內容在轉動中看起來是靜止的，"
                 + "所以你不必把手機拿起來就能讀。")
        }
    }

    private var howToPlace: some View {
        page(icon: "iphone.gen3", title: "手機怎麼擺") {
            bullet("**長邊中點對著轉軸。** 這樣手機的質心離轉軸最近（約 38 mm）；"
                   + "短邊對著轉軸的話會變成兩倍遠，不平衡力矩也跟著變大。")
            bullet("**螢幕朝上。** 物理上正反都能量，但朝下你看不到讀數，"
                   + "而且盤面上只要有一粒沙就刮螢幕。")
            bullet("**手機要水平。** 相機凸起會讓手機翹起約 0.8°，墊個小東西補平。")
            Divider().padding(.vertical, 6)
            note("**文字方向不對怎麼辦：** 量測畫面的內容鎖在「開始記錄那一瞬間"
                 + "手機的方向」，而那個瞬間是隨機的，所以文字可能朝著別的方向。"
                 + "用畫面下方的 ↺ ↻ 轉向鍵調到順眼為止 —— 設定會記住，"
                 + "只要你站的位置不變，下次通常就不用再調了。")
        }
    }

    // MARK: - 版面元件

    private func page<Content: View>(icon: String, title: String,
                                     @ViewBuilder content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 44))
                    .foregroundStyle(.tint)
                    .padding(.bottom, 4)
                Text(title)
                    .font(.title2.weight(.semibold))
                    .padding(.bottom, 4)
                content()
            }
            .padding(.horizontal, 28)
            .padding(.top, 40)
            .padding(.bottom, 60)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text("・").foregroundStyle(.secondary)
            Text(.init(text))          // .init 讓 markdown 生效
        }
        .font(.subheadline)
    }

    private func step(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(n)")
                .font(.caption.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 22, height: 22)
                .background(Circle().fill(.tint))
            Text(.init(text))
                .font(.subheadline)
        }
    }

    private func note(_ text: String) -> some View {
        Text(.init(text))
            .font(.caption)
            .foregroundStyle(.secondary)
    }
}

#Preview { OnboardingView() }
