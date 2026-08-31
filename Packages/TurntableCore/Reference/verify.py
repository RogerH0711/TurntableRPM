import numpy as np, reference as R, json, sys, os
G={}   # golden vectors
_results=[]   # 每一項的成敗，最後決定退出碼 —— CI 要能因為數學壞掉而變紅
def ok(name, got, exp, tol, unit=''):
    err = abs(got-exp)/(abs(exp) if exp else 1)
    passed = err<=tol
    _results.append((passed, name))
    print(f"{'PASS' if passed else 'FAIL'}  {name:<46} got={got:.6g}{unit} exp={exp:.6g}{unit} rel={100*err:.3f}% (tol {100*tol:.1f}%)")
    return passed

print("=== 1. 重力投影：手機傾斜不影響讀數 ===")
for tilt in [0,5,15,30]:
    s=R.synth(100/3, duration=5, tilt_deg=tilt)
    got=R.project(s['rot'],s['grav']).mean()
    ok(f"tilt {tilt}° -> °/s", got, 200.0, 1e-9, ' °/s')
naive=[]
for tilt in [0,5,15,30]:
    s=R.synth(100/3, duration=5, tilt_deg=tilt)
    nz=np.abs(np.rad2deg(s['rot'][:,2])).mean()   # 只讀 z 軸的錯誤做法
    naive.append((tilt, nz, 100*(nz/200-1)))
print("  對照：只讀 z 軸的 cos 誤差 ->", [f"{t}°:{e:+.3f}%" for t,_,e in naive])
G['projection_naive_z_error_pct']={str(t):round(e,4) for t,_,e in naive}

print("\n=== 2. 平均轉速與標稱辨識 ===")
for nom in R.NOMINALS:
    s=R.synth(nom, duration=30, wow=[(0.3,0.55,0.0)], noise_pct=0.02)
    w=R.project(s['rot'],s['grav']); m=R.mean_omega(s['t'],w); rpm=m/6
    ok(f"nominal {nom:.4f} RPM 回推", rpm, nom, 5e-4, ' RPM')
    assert R.classify(rpm)==nom
print("  辨識邊界：")
for rpm,exp in [(30.667,100/3),(30.66,None),(36.0,100/3),(41.4,45),(41.3,None),(71.76,78),(71.7,None)]:
    got=R.classify(rpm); print(f"    {rpm:7.3f} RPM -> {got}  ({'OK' if got==exp else 'MISMATCH'})")
G['classify_boundaries']={'33.333_lo':round(100/3*0.92,4),'33.333_hi':round(100/3*1.08,4),
                          '45_lo':round(45*0.92,4),'78_lo':round(78*0.92,4)}

print("\n=== 3. 移動平均頻率響應 ===")
for n in [10,25,50,100,300]:
    null=R.FS/n; m3=0.4429*R.FS/n
    print(f"  N={n:4d}  第一零點 {null:6.3f} Hz  (實測 |H|={R.ma_response(null,n):.2e})   -3dB {m3:6.3f} Hz  (實測 {20*np.log10(R.ma_response(m3,n)):+.3f} dB)")
G['moving_average']={str(n):{'null_hz':round(R.FS/n,4),'minus3db_hz':round(0.4429*R.FS/n,4)} for n in [10,25,50,100,300]}

print("\n=== 4. WRMS：純正弦注入 ===")
for A,f in [(0.50,4.0),(0.50,0.8),(0.50,20.0),(1.00,4.0),(0.20,2.0)]:
    s=R.synth(100/3, duration=60, wow=[(A,f,0.0)])
    d=(s['omega_true']-s['omega_true'].mean())/s['omega_true'].mean()*100
    got=R.wrms(d); exp=A*R.weight(f)/np.sqrt(2)
    ok(f"A={A}% @ {f} Hz", got, exp, 0.02, '%')
    G.setdefault('wrms_sine',[]).append({'amp_pct':A,'freq_hz':f,'expected_wrms_pct':round(float(exp),6)})

print("\n=== 5. 2σ 峰值 / WRMS 比值 ===")
s=R.synth(100/3, duration=120, wow=[(0.5,4.0,0.0)])
d=(s['omega_true']-s['omega_true'].mean())/s['omega_true'].mean()*100
r_sin=R.peak_2sigma(d)/R.wrms(d); ok("純正弦 4 Hz", r_sin, 1.410, 0.03)
s=R.synth(100/3, duration=120, noise_pct=0.5, seed=7)
d=(s['omega_true']-s['omega_true'].mean())/s['omega_true'].mean()*100
r_g=R.peak_2sigma(d)/R.wrms(d); ok("高斯白雜訊", r_g, 1.960, 0.05)
G['ratio_sine']=round(float(r_sin),4); G['ratio_gauss']=round(float(r_g),4)

print("\n=== 6. 極座標分箱：每圈一次，已知相位 ===")
for ph_deg in [0.0, 90.0, 217.0]:
    frev=(100/3)/60
    s=R.synth(100/3, duration=60, wow=[(0.4,frev,np.deg2rad(ph_deg))])
    d=(s['omega_true']-s['omega_true'].mean())/s['omega_true'].mean()*100
    bins,cnt=R.polar_bins(s['theta_true'],d)
    peak=int(np.argmax(bins)); peak_deg=peak*5+2.5
    exp=(90.0-ph_deg)%360.0
    print(f"  相位 {ph_deg:6.1f}° -> 峰值落在第 {peak:2d} 格 ({peak_deg:6.1f}°)  預期 {exp:6.1f}°  差 {abs(((peak_deg-exp+180)%360)-180):.1f}°  每格樣本數 {cnt.min()}–{cnt.max()}")
G['polar_samples_per_bin_60s']=int(round(100*60/72))

print("\n=== 7. 指南針校準：回推比例因子 ===")
for eps in [0.03,0.01,-0.015,0.001]:
    s=R.synth(100/3, duration=120, scale_err=eps, yaw_noise_deg=5.0, seed=3)
    w=R.project(s['rot'],s['grav'])
    k,nrev=R.calibrate(s['t'],w,s['yaw'])
    ok(f"ε={eps:+.3f} 回推 k ({nrev} 圈)", k, 1.0/(1.0+eps), 0.002)
G['calibration']= [{'scale_err':e,'expected_k':round(1.0/(1.0+e),6)} for e in [0.03,0.01,-0.015,0.001]]

print("\n=== 8. 載重外插 ===")
rpm0,s_=R.extrapolate_zero_load(33.300,33.240,100.0,170.0)
ok("斜率 -0.0006 RPM/g，外插回零負載", rpm0, 33.402, 1e-9,' RPM')
print(f"  斜率 s = {s_:.6f} RPM/g")
G['load']={'rpm1':33.300,'rpm2':33.240,'delta_m_g':100.0,'phone_m_g':170.0,
           'expected_slope':round(float(s_),8),'expected_rpm0':round(float(rpm0),6)}

print("\n=== 9. 雜訊底線（規格 §2.3 對照） ===")
for T in [1,10,60]:
    s=R.synth(100/3, duration=max(T,5), noise_pct=0.035, seed=11)
    w=R.project(s['rot'],s['grav'])
    seg=w[:int(T*R.FS)]; t=s['t'][:int(T*R.FS)]
    sd=np.std(seg)/np.mean(seg)*100/np.sqrt(len(seg))
    print(f"  T={T:3d}s  平均值標準誤 {sd:.5f}%   (規格表: 1s 0.0035 / 10s 0.0011 / 60s 0.00046)")

print("\n=== 黃金向量 ===")
print(json.dumps(G, ensure_ascii=False, indent=1)[:2000])
# 寫在這支腳本旁邊，不是 cwd —— 從別的目錄呼叫時才不會把檔案掉在錯的地方
_out = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'golden.json')
open(_out,'w').write(json.dumps(G, ensure_ascii=False, indent=1))

failed=[n for p_,n in _results if not p_]
print(f"\n=== {len(_results)-len(failed)}/{len(_results)} 通過 ===")
if failed:
    for n in failed: print(f"  FAIL  {n}")
    sys.exit(1)
