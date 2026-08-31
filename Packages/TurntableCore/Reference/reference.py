"""TurntableCore 參考實作 — 用來產生 Swift 單元測試的黃金向量。"""
import numpy as np

FS = 100.0
NOMINALS = [50.0/3.0, 100.0/3.0, 45.0, 78.0]   # RPM

# ---------- 3.4 加權曲線 ----------
F1, F2 = 0.635, 24.8
def _w_raw(f):
    f = np.asarray(f, float)
    with np.errstate(divide='ignore', invalid='ignore'):
        hp = (f/F1)**3 / (1.0 + (f/F1)**2)**1.5
        lp = 1.0 / (1.0 + (f/F2)**2)**1.5
    out = hp*lp
    return np.where(f > 0, out, 0.0)

_PEAK = _w_raw(np.logspace(-3, 4, 400000)).max()
def weight(f):
    return _w_raw(f) / _PEAK

# ---------- 2.2 重力投影 ----------
def project(rot, grav):
    """rot, grav: (...,3) 裝置座標系。回傳 °/s（取絕對值）。"""
    rot = np.asarray(rot, float); grav = np.asarray(grav, float)
    gm = np.linalg.norm(grav, axis=-1)
    omega = -np.sum(rot*grav, axis=-1)/gm          # rad/s
    return np.abs(omega)*180.0/np.pi

# ---------- 3.2 平均與標稱辨識 ----------
def mean_omega(t, omega):
    """梯形積分平均，容忍取樣間隔抖動。"""
    return np.trapezoid(omega, t)/(t[-1]-t[0])

def classify(rpm, tol=0.08):
    best, bd = None, 1e9
    for n in NOMINALS:
        d = abs(rpm-n)/n
        if d < bd: best, bd = n, d
    return best if bd <= tol else None

# ---------- 3.3 移動平均 ----------
def moving_average(x, n):
    k = np.ones(n)/n
    return np.convolve(x, k, mode='same')

def ma_response(f, n, fs=FS):
    """移動平均的振幅響應 |H(f)|"""
    f = np.asarray(f, float)
    num = np.sin(np.pi*f*n/fs); den = n*np.sin(np.pi*f/fs)
    return np.where(f == 0, 1.0, np.abs(np.divide(num, den, out=np.ones_like(f), where=den != 0)))

# ---------- 3.4 加權濾波（整段頻域） ----------
def weighted_series(d, fs=FS):
    """整段 FFT → 乘 W(f) → IFFT。回傳加權後的時域序列（同長度）。"""
    n = len(d)
    nfft = 1 << (int(np.ceil(np.log2(n)))+1)      # 補零到 2 的次方 x2，避免循環卷積
    D = np.fft.rfft(d, n=nfft)
    f = np.fft.rfftfreq(nfft, 1.0/fs)
    y = np.fft.irfft(D*weight(f), n=nfft)
    return y[:n]

def wrms(d, fs=FS, guard=2.0):
    y = weighted_series(d, fs); g = int(guard*fs)
    core = y[g:len(y)-g] if len(y) > 2*g else y
    return float(np.sqrt(np.mean(core**2)))

def peak_2sigma(d, fs=FS, guard=2.0):
    y = weighted_series(d, fs); g = int(guard*fs)
    core = y[g:len(y)-g] if len(y) > 2*g else y
    return float(np.percentile(np.abs(core), 95.0))

# ---------- 3.6 極座標分箱 ----------
def polar_bins(theta_deg, d, nbins=72):
    idx = (np.floor(np.mod(theta_deg, 360.0)/(360.0/nbins))).astype(int)
    s = np.zeros(nbins); c = np.zeros(nbins, int)
    np.add.at(s, idx, d); np.add.at(c, idx, 1)
    return np.divide(s, np.maximum(c, 1)), c

# ---------- 3.7 校準 ----------
def calibrate(t, omega_deg, yaw_rad):
    """回傳倍率 k = Θ_mag / Θ_gyro，時間窗對齊整數圈。"""
    theta_g = np.concatenate([[0.0], np.cumsum(np.diff(t)*(omega_deg[:-1]+omega_deg[1:])/2)])
    theta_m = np.unwrap(yaw_rad)*180.0/np.pi
    theta_m = np.abs(theta_m - theta_m[0])
    nrev = int(np.floor(theta_m[-1]/360.0))
    if nrev < 1: return None, 0
    i1 = int(np.argmax(theta_m >= nrev*360.0))
    return float(theta_m[i1]/theta_g[i1]), nrev

# ---------- 3.8 載重外插 ----------
def extrapolate_zero_load(rpm1, rpm2, delta_m, phone_m):
    s = (rpm2-rpm1)/delta_m
    return rpm1 - s*phone_m, s

# ---------- 合成訊號 ----------
def synth(nominal_rpm, duration=60.0, fs=FS, wow=(), noise_pct=0.0,
          scale_err=0.0, tilt_deg=0.0, yaw_noise_deg=0.0, seed=1):
    """wow: [(振幅%, 頻率Hz, 相位rad), ...]"""
    rng = np.random.default_rng(seed)
    n = int(duration*fs); t = np.arange(n)/fs
    w0 = nominal_rpm*6.0                                   # °/s
    dev = np.zeros(n)
    for a, f, ph in wow:
        dev += (a/100.0)*np.sin(2*np.pi*f*t + ph)
    if noise_pct: dev += rng.normal(0, noise_pct/100.0, n)
    omega_true = w0*(1.0+dev)                              # °/s 真值
    theta_true = np.concatenate([[0.0], np.cumsum(np.diff(t)*(omega_true[:-1]+omega_true[1:])/2)])
    yaw = np.deg2rad(theta_true) + np.deg2rad(rng.normal(0, yaw_noise_deg, n) if yaw_noise_deg else 0)
    omega_meas = omega_true*(1.0+scale_err)                # 陀螺儀量到的
    # 三軸：手機傾斜 tilt_deg，自轉軸沿重力
    tr = np.deg2rad(tilt_deg)
    g = np.tile(np.array([np.sin(tr), 0.0, -np.cos(tr)]), (n, 1))
    rot = (np.deg2rad(omega_meas)[:, None])*(-g)
    return dict(t=t, omega_true=omega_true, omega_meas=omega_meas,
                rot=rot, grav=g, yaw=yaw, theta_true=theta_true, w0=w0)
