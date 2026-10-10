import subprocess, numpy as np, sys
SR=22050
def load(path, ss=None, t=None):
    cmd=['ffmpeg','-v','error']
    if ss is not None: cmd+=['-ss',str(ss)]
    if t is not None: cmd+=['-t',str(t)]
    cmd+=['-i',path,'-vn','-ac','1','-ar',str(SR),'-f','f32le','-']
    return np.frombuffer(subprocess.run(cmd,capture_output=True).stdout,dtype=np.float32)
def onset_env(x, n=1024, hop=256):
    w=np.hanning(n)
    frames=1+(len(x)-n)//hop
    idx=np.arange(n)[None,:]+hop*np.arange(frames)[:,None]
    S=np.abs(np.fft.rfft(x[idx]*w,axis=1))
    S=np.log1p(30*S)
    flux=np.maximum(0,np.diff(S,axis=0)).sum(axis=1)
    low=np.maximum(0,np.diff(S[:,:12],axis=0)).sum(axis=1)   # ~0..260 Hz: бочка
    return flux, low, SR/hop
def tempo(env, fps, lo=70, hi=190):
    e=env-env.mean()
    ac=np.correlate(e,e,'full')[len(e)-1:]
    best=[]
    for bpm in np.arange(lo,hi,0.25):
        lag=60.0/bpm*fps
        s=0
        for k in (1,2,3,4):
            i=lag*k; i0=int(i); f=i-i0
            if i0+1<len(ac): s+=(ac[i0]*(1-f)+ac[i0+1]*f)/k
        best.append((s,bpm))
    best.sort(reverse=True)
    return best[:5]
def phase(env,fps,bpm):
    period=60.0/bpm*fps
    best=(-1,0)
    for ph in np.linspace(0,period,200,endpoint=False):
        pos=np.arange(ph,len(env)-1,period)
        i0=pos.astype(int); f=pos-i0
        s=(env[i0]*(1-f)+env[i0+1]*f).mean()
        if s>best[0]: best=(s,ph)
    return best[1]/fps
if __name__=='__main__':
    for path in sys.argv[1:]:
        x=load(path)
        flux,low,fps=onset_env(x)
        print('==',path,'dur',round(len(x)/SR,2))
        for name,env in (('flux',flux),('low',low)):
            tp=tempo(env,fps)
            print(' ',name,'tempo candidates',[(round(b,2),round(s/tp[0][0],2)) for s,b in tp])
        # громкость по секундам
        rms=[float(np.sqrt((x[i:i+SR]**2).mean())) for i in range(0,len(x)-SR,SR)]
        print('  rms/sec',' '.join(f'{int(r*100):02d}' for r in rms))
