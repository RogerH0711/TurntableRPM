import Foundation

/// 頻譜上的一根峰，附帶「它是什麼」的判讀。
///
/// 光列出一堆頻率沒有用。真正有診斷價值的是**它跟轉盤基頻的關係**：
/// 整數倍代表跟著盤面轉的東西（偏心、盤面變形），非整數倍代表傳動鏈上
/// 轉速不同的零件（馬達、皮帶輪）。
public struct SpectralPeak: Sendable, Equatable {
    public let frequencyHz: Double
    /// 振幅，單位是「佔平均轉速的百分比」。
    public let amplitudePercent: Double
    /// 相對於轉盤基頻的倍數。1.0 = 每圈一次。
    public let orderOfRotation: Double

    /// 是不是轉盤的整數諧波（容差 4%）。
    public var isRotationHarmonic: Bool {
        let n = orderOfRotation.rounded()
        return n >= 1 && abs(orderOfRotation - n) < 0.04
    }

    /// 給使用者看的一句判讀。
    public var interpretation: String {
        let n = Int(orderOfRotation.rounded())
        if isRotationHarmonic {
            switch n {
            case 1:  return "每圈一次 —— 偏心（盤面、主軸或皮帶接觸面沒對正）"
            case 2:  return "每圈兩次 —— 盤面橢圓或主軸兩點磨損"
            default: return "轉盤 \(n)× 諧波"
            }
        }
        if orderOfRotation < 1 {
            return "比一圈還慢 —— 皮帶循環或長週期漂移"
        }
        return String(format: "非諧波（轉盤的 %.1f 倍）—— 傳動鏈上的零件，"
                      + "馬達或皮帶輪的候選", orderOfRotation)
    }
}

/// 一次量測的完整離線分析。
///
/// **這一層刻意放在核心而不是 App**：它能在 Linux CI 上跑測試，也能被
/// `tools/analyze_export.py` 的結果交叉比對。App 端只負責畫圖。
///
/// 分析路徑一律用**未平滑**的偏差序列（見 CLAUDE.md 坑 2）。移動平均在 fs/N
/// 有零點，100 Hz 下 N=25 的零點正好落在加權曲線的 4 Hz 峰值上。
public struct MeasurementAnalysis: Sendable {
    public let meanRPM: Double
    public let sampleRate: Double
    public let durationSeconds: Double
    /// 轉盤基頻（Hz）。1 / 每圈秒數。
    public let rotationHz: Double

    /// 未平滑的瞬時偏差序列，%。滾動圖用。
    public let deviationPercent: [Double]
    public let wowFlutter: WowFlutterResult

    public let spectrumFrequencies: [Double]
    public let spectrumAmplitudes: [Double]
    /// 依振幅排序的顯著譜峰。
    public let peaks: [SpectralPeak]

    /// 依圈內角度分箱的平均偏差。極座標熱圖用。
    public let polarBins: [PolarBin]
    public let peakAngleDegrees: Double?

    /// 實際拿來分析的區間。開頭的加速、尾端的減速、中途的干擾都在這裡被切掉。
    /// **所有數字都是這個區間算出來的**，不是整段量測。
    public let stableWindow: StableWindow
    /// 被切掉的秒數（開頭、尾端）。要在畫面上如實告訴使用者。
    public let trimmedStartSeconds: Double
    public let trimmedEndSeconds: Double

    /// 每圈一次成分的振幅（%）。偏心的直接指標。
    public var onePerRevolutionPercent: Double {
        peaks.first { $0.isRotationHarmonic && Int($0.orderOfRotation.rounded()) == 1 }?
            .amplitudePercent ?? 0
    }

    // MARK: - 分析

    /// - Parameters:
    ///   - samples: 原始樣本。時間戳可以有抖動，內部會重取樣到等間隔。
    ///   - sampleRate: 重取樣的目標頻率。
    ///   - binCount: 極座標分箱數。
    public static func analyze(samples: [SpinSample],
                               sampleRate: Double = 100.0,
                               binCount: Int = 72) -> MeasurementAnalysis? {
        // 先切出穩定區間再分析。少了這一步，一段從靜止開始的加速會在頻譜低頻端
        // 灌進巨大能量，把每圈一次的偏心峰淹掉 —— 診斷會整個錯掉。
        guard samples.count > 64, let window = StabilityGate.find(samples) else { return nil }
        let trimmedStart = window.droppedAtStart > 0
            ? samples[window.range.lowerBound].t - samples[0].t : 0
        let trimmedEnd = window.droppedAtEnd > 0
            ? samples[samples.count - 1].t - samples[window.range.upperBound - 1].t : 0
        let stable = Array(samples[window.range])

        guard let resampled = UniformResampler.resample(stable, sampleRate: sampleRate),
              let (meanOmega, deviation) = DeviationSeries.make(from: resampled.values),
              meanOmega > 0,
              let wf = WowFlutterAnalyzer.analyze(deviationPercent: deviation,
                                                  sampleRate: sampleRate)
        else { return nil }

        let duration = Double(resampled.values.count - 1) / sampleRate
        let rotationHz = meanOmega / 360.0

        let spectrum = FFT.amplitudeSpectrum(deviation, sampleRate: sampleRate)
        let peaks = findPeaks(frequencies: spectrum.frequencies,
                              amplitudes: spectrum.amplitudes,
                              rotationHz: rotationHz)

        // 極座標分箱。角度用等間隔網格上的累積轉角推算 —— 重取樣之後每一步
        // 的時間都是 1/fs，所以角度就是 ω 的累加。
        var polar = PolarAccumulator(binCount: binCount)
        var angle = 0.0
        for (i, omega) in resampled.values.enumerated() {
            polar.add(angleDegrees: angle, deviationPercent: deviation[i])
            angle += omega / sampleRate
        }

        return MeasurementAnalysis(
            meanRPM: meanOmega / 6.0,
            sampleRate: sampleRate,
            durationSeconds: duration,
            rotationHz: rotationHz,
            deviationPercent: deviation,
            wowFlutter: wf,
            spectrumFrequencies: spectrum.frequencies,
            spectrumAmplitudes: spectrum.amplitudes,
            peaks: peaks,
            polarBins: polar.bins,
            peakAngleDegrees: polar.peakAngleDegrees,
            stableWindow: window,
            trimmedStartSeconds: trimmedStart,
            trimmedEndSeconds: trimmedEnd)
    }

    /// 找局部極大值。
    ///
    /// 門檻用「整段頻譜的中位數振幅」的倍數，而不是固定值 —— 不同量測的雜訊
    /// 底線差很多，固定門檻在安靜的量測裡會漏掉真峰、在吵的量測裡會塞滿雜訊。
    ///
    /// 只看 0.05–50 Hz：更低的是量測時長不夠解析的漂移，更高的超出 100 Hz
    /// 取樣的可用範圍。
    static func findPeaks(frequencies: [Double],
                          amplitudes: [Double],
                          rotationHz: Double,
                          maxCount: Int = 12) -> [SpectralPeak] {
        guard frequencies.count == amplitudes.count, frequencies.count > 8, rotationHz > 0
        else { return [] }

        let band = amplitudes.indices.filter { frequencies[$0] > 0.05 && frequencies[$0] < 50 }
        guard band.count > 8 else { return [] }

        let sorted = band.map { amplitudes[$0] }.sorted()
        let median = sorted[sorted.count / 2]
        let threshold = max(median * 6.0, 1e-9)

        var found: [SpectralPeak] = []
        for i in band where i > 0 && i < amplitudes.count - 1 {
            let a = amplitudes[i]
            guard a > threshold, a > amplitudes[i - 1], a >= amplitudes[i + 1] else { continue }
            found.append(SpectralPeak(frequencyHz: frequencies[i],
                                      amplitudePercent: a,
                                      orderOfRotation: frequencies[i] / rotationHz))
        }
        found.sort { $0.amplitudePercent > $1.amplitudePercent }
        return Array(found.prefix(maxCount))
    }
}
