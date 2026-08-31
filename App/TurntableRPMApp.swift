import SwiftUI
import SwiftData

@main
struct TurntableRPMApp: App {
    var body: some Scene {
        WindowGroup {
            LiveMeasurementView()
        }
        .modelContainer(for: [MeasurementRecord.self, TurntableProfile.self])
    }
}
