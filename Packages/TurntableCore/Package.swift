// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TurntableCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [
        .library(name: "TurntableCore", targets: ["TurntableCore"])
    ],
    targets: [
        .target(name: "TurntableCore"),
        .testTarget(name: "TurntableCoreTests", dependencies: ["TurntableCore"])
    ]
)
