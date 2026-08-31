import SwiftUI

/// 第一次開啟時的導覽。
///
/// **刻意寫得很短。** 第一版每頁塞五六條說明，字小又多，沒有人會看 ——
/// 導覽要做的是讓人能開始用，不是把原理講完。深入的內容放在說明頁（`AboutView`），
/// 想知道的人自己會去看。
///
/// 唯一不能省的是第二頁的擺法圖：那不只是說明，它讓量測畫面的文字方向
/// 從「隨機」變成「固定正對使用者」（見 `PlacementDiagram`）。
struct OnboardingView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var page = 0
    private let lastPage = 3

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $page) {
                intro.tag(0)
                placement.tag(1)
                steps.tag(2)
                limits.tag(3)
            }
            // 關掉 TabView 自己的圓點，改畫在底部固定列。
            // 內建的圓點是浮在內容上的 —— 字體調大之後內文變長，圓點就會壓在
            // 文字上。目標族群正是會把字體調大的人。
            .tabViewStyle(.page(indexDisplayMode: .never))

            // 底部固定列。**背景要不透明** —— TabView 的捲動內容不會被裁在邊界上，
            // 字體調大之後內文會一路長到這裡，透明背景就會看到文字疊在圓點與按鈕上。
            VStack(spacing: 16) {
                HStack(spacing: 8) {
                    ForEach(0 ... lastPage, id: \.self) { i in
                        Circle()
                            .fill(i == page ? Color.accentColor : Color.secondary.opacity(0.3))
                            .frame(width: 8, height: 8)
                    }
                }
                Button(page == lastPage ? "開始使用" : "下一步") {
                    if page == lastPage { dismiss() } else { withAnimation { page += 1 } }
                }
                .font(.headline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 24)
            }
            .padding(.top, 14)
            .padding(.bottom, 20)
            .frame(maxWidth: .infinity)
            .background(Color(.systemBackground))
        }
    }

    private var intro: some View {
        page(icon: "record.circle", title: "量唱盤的轉速") {
            Text("把手機放在轉動的唱盤上，就能量出轉速、偏差和抖晃率。")
            Text("不需要頻閃盤或其他硬體。")
                .foregroundStyle(.secondary)
        }
    }

    private var placement: some View {
        page(icon: nil, title: "手機這樣擺") {
            PlacementGuide()
        }
    }

    private var steps: some View {
        page(icon: "list.number", title: "怎麼量") {
            step(1, "轉盤停著，把手機照上一頁的方式放好，**對面記得配平**")
            step(2, "按下「準備好，開始偵測」")
            step(3, "啟動轉盤 —— 轉速穩了會自動開始，停下時自動結束")
            Text("量 90 秒以上，頻譜才夠清楚。")
                .font(.body)
                .foregroundStyle(.secondary)
                .padding(.top, 4)
        }
    }

    private var limits: some View {
        page(icon: "exclamationmark.triangle", title: "兩件要先知道的事") {
            VStack(alignment: .leading, spacing: 6) {
                Text("**它量的是盤，不是唱片。**")
                Text("唱片中心孔偏心造成的抖動，這個方法看不到。")
                    .foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("**校準之前，偏差 % 不能拿來調唱盤。**")
                Text("那是唱盤誤差和陀螺儀誤差相乘的結果。校準用碼錶做，一支手機做一次就好。")
                    .foregroundStyle(.secondary)
            }
            Text("詳細說明在右上角的「說明」裡。")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .padding(.top, 6)
        }
    }

    // MARK: - 版面

    private func page<Content: View>(icon: String?, title: String,
                                     @ViewBuilder content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let icon {
                    Image(systemName: icon)
                        .font(.system(size: 40))
                        .foregroundStyle(.tint)
                }
                Text(title)
                    .font(.title.weight(.bold))
                content()
            }
            // 字級整體放大：內文用 body 不是 subheadline。
            .font(.body)
            .padding(.horizontal, 28)
            .padding(.top, 44)
            .padding(.bottom, 40)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func step(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(n)")
                .font(.body.weight(.bold))
                .foregroundStyle(.white)
                .frame(width: 26, height: 26)
                .background(Circle().fill(.tint))
            Text(.init(text))
        }
    }
}

#Preview { OnboardingView() }
