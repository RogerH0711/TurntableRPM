import Foundation

/// FFT 前必做：CoreMotion 的派送間隔會抖動，先線性內插到等間隔網格。
public enum UniformResampler {
    public static func resample(_ samples: [SpinSample],
                                sampleRate: Double) -> (startTime: TimeInterval, values: [Double])? {
        guard samples.count >= 2, sampleRate > 0 else { return nil }
        let start = samples[0].t
        let span = samples[samples.count - 1].t - start
        guard span > 0 else { return nil }

        let count = Int(span * sampleRate) + 1
        var out = [Double](repeating: 0, count: count)
        var j = 0
        for i in 0 ..< count {
            let t = start + Double(i) / sampleRate
            while j + 2 < samples.count && samples[j + 1].t < t { j += 1 }
            let a = samples[j]
            let b = samples[j + 1]
            let dt = b.t - a.t
            var u = dt > 0 ? (t - a.t) / dt : 0
            u = min(max(u, 0), 1)
            out[i] = a.omega + (b.omega - a.omega) * u
        }
        return (start, out)
    }
}
