import Foundation

/// 純 Swift 的 radix-2 Cooley–Tukey FFT。
///
/// 刻意不用 Accelerate/vDSP：這個 target 要能在 Mac 上以純 Swift 跑測試，
/// 也方便日後移植。正式 app 若要加速，可在 iOS 端換成 vDSP 並用同一組測試把關。
public enum FFT {

    public static func isPowerOfTwo(_ n: Int) -> Bool {
        n > 0 && (n & (n - 1)) == 0
    }

    public static func nextPowerOfTwo(atLeast n: Int) -> Int {
        var size = 1
        while size < n { size <<= 1 }
        return size
    }

    /// 原地轉換。`real.count` 必須是 2 的次冪，且與 `imag.count` 相同。
    /// `inverse == true` 時輸出已除以 N。
    public static func transform(real: inout [Double], imag: inout [Double], inverse: Bool = false) {
        let n = real.count
        precondition(n == imag.count, "real 與 imag 長度必須相同")
        precondition(isPowerOfTwo(n), "長度必須是 2 的次冪")
        if n == 1 { return }

        // 位元反轉排序
        var j = 0
        for i in 0 ..< (n - 1) {
            if i < j {
                real.swapAt(i, j)
                imag.swapAt(i, j)
            }
            var k = n >> 1
            while k <= j {
                j -= k
                k >>= 1
            }
            j += k
        }

        // 蝶形運算
        var length = 2
        while length <= n {
            let half = length / 2
            let base = (inverse ? 2.0 : -2.0) * Double.pi / Double(length)
            var start = 0
            while start < n {
                for k in 0 ..< half {
                    let angle = base * Double(k)
                    let wr = cos(angle)
                    let wi = sin(angle)
                    let a = start + k
                    let b = a + half
                    let tr = real[b] * wr - imag[b] * wi
                    let ti = real[b] * wi + imag[b] * wr
                    real[b] = real[a] - tr
                    imag[b] = imag[a] - ti
                    real[a] += tr
                    imag[a] += ti
                }
                start += length
            }
            length <<= 1
        }

        if inverse {
            let scale = 1.0 / Double(n)
            for i in 0 ..< n {
                real[i] *= scale
                imag[i] *= scale
            }
        }
    }

    /// 實數輸入的單邊振幅頻譜（含 Hann 窗與振幅修正），供頻譜圖使用。
    public static func amplitudeSpectrum(_ x: [Double], sampleRate: Double) -> (frequencies: [Double], amplitudes: [Double]) {
        let n = x.count
        guard n >= 4, sampleRate > 0 else { return ([], []) }
        let size = nextPowerOfTwo(atLeast: n)
        var real = [Double](repeating: 0, count: size)
        var imag = [Double](repeating: 0, count: size)
        var windowSum = 0.0
        for i in 0 ..< n {
            let w = 0.5 - 0.5 * cos(2.0 * Double.pi * Double(i) / Double(n - 1))
            real[i] = x[i] * w
            windowSum += w
        }
        transform(real: &real, imag: &imag, inverse: false)

        let bins = size / 2 + 1
        var frequencies = [Double](repeating: 0, count: bins)
        var amplitudes = [Double](repeating: 0, count: bins)
        let df = sampleRate / Double(size)
        for k in 0 ..< bins {
            frequencies[k] = Double(k) * df
            let magnitude = (real[k] * real[k] + imag[k] * imag[k]).squareRoot()
            let scale = (k == 0 || k == size / 2) ? 1.0 : 2.0
            amplitudes[k] = magnitude * scale / windowSum
        }
        return (frequencies, amplitudes)
    }
}
