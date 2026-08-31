import SwiftUI

/// 「標籤 —— 數值 單位」這一列，三個畫面共用。
struct DiagnosticRow: View {
    // 標籤是 LocalizedStringKey，呼叫端的字面值才會被抽進 String Catalog。
    // 數值與單位是執行期算出來的字串，不翻譯。
    let label: LocalizedStringKey
    let value: String
    var unit: String = ""

    init(_ label: LocalizedStringKey, _ value: String, _ unit: String = "") {
        self.label = label
        self.value = value
        self.unit = unit
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Text(value)
                .monospacedDigit()
            if !unit.isEmpty {
                Text(unit)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .font(.body)
        .padding(.vertical, 4)
    }
}

extension View {
    /// 圓角卡片。畫面上每個區塊都是這個樣子。
    func measurementCard() -> some View {
        self
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}

/// 非有限值（NaN／無限大）要顯示成破折號，不要印出 "nan"。
func formatted(_ value: Double, _ spec: String) -> String {
    value.isFinite ? String(format: spec, value) : "—"
}
