package local.originalplayer.bridge;
/** Original probe implementation. Concrete methods preserve JNI return signatures. */
public final class ConcreteObserver implements com.ss.videoarch.liveplayer2.VeLivePlayerObserver {
    private final java.lang.reflect.InvocationHandler handler;
    public ConcreteObserver(java.lang.reflect.InvocationHandler handler) { this.handler = handler; }
    @Override public void didAutomaticallySwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a2, org.json.JSONObject a3) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("didAutomaticallySwitch", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolution.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolution.class, org.json.JSONObject.class}), new Object[]{a0, a1, a2, a3}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onAudioDeviceClose(com.ss.videoarch.liveplayer2.VeLivePlayer a0) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onAudioDeviceClose", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class}), new Object[]{a0}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onAudioDeviceOpen(com.ss.videoarch.liveplayer2.VeLivePlayer a0, int a1, int a2, int a3) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onAudioDeviceOpen", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, int.class, int.class, int.class}), new Object[]{a0, a1, a2, a3}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onAudioDeviceRelease(com.ss.videoarch.liveplayer2.VeLivePlayer a0) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onAudioDeviceRelease", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class}), new Object[]{a0}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onAudioFrameRenderMetaInfo(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioFrameRenderMetaInfo a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onAudioFrameRenderMetaInfo", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerAudioFrameRenderMetaInfo.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onAudioRenderStall(com.ss.videoarch.liveplayer2.VeLivePlayer a0, long a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onAudioRenderStall", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, long.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onAudioVolume(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioVolume a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onAudioVolume", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerAudioVolume.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onDrawFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, android.hardware.HardwareBuffer a1, int[] a2, int a3, int a4) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onDrawFrame", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, android.hardware.HardwareBuffer.class, int[].class, int.class, int.class}), new Object[]{a0, a1, a2, a3, a4}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onError(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerError a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onError", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerError.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onFirstAudioFrameRender(com.ss.videoarch.liveplayer2.VeLivePlayer a0, boolean a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onFirstAudioFrameRender", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, boolean.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onFirstVideoFrameRender(com.ss.videoarch.liveplayer2.VeLivePlayer a0, boolean a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onFirstVideoFrameRender", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, boolean.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onHeadPoseUpdate(com.ss.videoarch.liveplayer2.VeLivePlayer a0, float a1, float a2, float a3, float a4, float a5, float a6, float a7) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onHeadPoseUpdate", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class}), new Object[]{a0, a1, a2, a3, a4, a5, a6, a7}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onLoudness(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioLoudnessInfo a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onLoudness", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerAudioLoudnessInfo.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onMainBackupSwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerStreamType a1, com.ss.videoarch.liveplayer2.VeLivePlayerError a2) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onMainBackupSwitch", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerStreamType.class, com.ss.videoarch.liveplayer2.VeLivePlayerError.class}), new Object[]{a0, a1, a2}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onMonitorLog(com.ss.videoarch.liveplayer2.VeLivePlayer a0, java.lang.String a1, java.lang.String a2) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onMonitorLog", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, java.lang.String.class, java.lang.String.class}), new Object[]{a0, a1, a2}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public java.lang.String onMuteIssueWarning(com.ss.videoarch.liveplayer2.VeLivePlayer a0) {
        try { return (java.lang.String) handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onMuteIssueWarning", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class}), new Object[]{a0}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onNetworkQualityChanged(com.ss.videoarch.liveplayer2.VeLivePlayer a0, int a1, java.lang.String a2) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onNetworkQualityChanged", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, int.class, java.lang.String.class}), new Object[]{a0, a1, a2}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onPlayerStatusUpdate(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerStatus a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onPlayerStatusUpdate", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerStatus.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onReceiveBinarySeiMessage(com.ss.videoarch.liveplayer2.VeLivePlayer a0, java.nio.ByteBuffer a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onReceiveBinarySeiMessage", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, java.nio.ByteBuffer.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onReceiveSeiMessage(com.ss.videoarch.liveplayer2.VeLivePlayer a0, java.lang.String a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onReceiveSeiMessage", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, java.lang.String.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onRenderAudioFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioFrame a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onRenderAudioFrame", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerAudioFrame.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onRenderVideoFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerVideoFrame a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onRenderVideoFrame", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerVideoFrame.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onResolutionDegrade(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onResolutionDegrade", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolution.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onResolutionSwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1, com.ss.videoarch.liveplayer2.VeLivePlayerError a2, com.ss.videoarch.liveplayer2.VeLivePlayerResolutionSwitchReason a3) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onResolutionSwitch", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolution.class, com.ss.videoarch.liveplayer2.VeLivePlayerError.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolutionSwitchReason.class}), new Object[]{a0, a1, a2, a3}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onSnapshotComplete(com.ss.videoarch.liveplayer2.VeLivePlayer a0, android.graphics.Bitmap a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onSnapshotComplete", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, android.graphics.Bitmap.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onStallEnd(com.ss.videoarch.liveplayer2.VeLivePlayer a0) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onStallEnd", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class}), new Object[]{a0}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onStallStart(com.ss.videoarch.liveplayer2.VeLivePlayer a0) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onStallStart", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class}), new Object[]{a0}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onStatistics(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerStatistics a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onStatistics", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerStatistics.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onStreamFailedOpenSuperResolution(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerError a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onStreamFailedOpenSuperResolution", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerError.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onTextureRenderDrawFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, android.view.Surface a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onTextureRenderDrawFrame", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, android.view.Surface.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onVideoFrameRenderMetaInfo(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerVideoFrameRenderMetaInfo a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onVideoFrameRenderMetaInfo", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerVideoFrameRenderMetaInfo.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onVideoRenderStall(com.ss.videoarch.liveplayer2.VeLivePlayer a0, long a1) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onVideoRenderStall", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, long.class}), new Object[]{a0, a1}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public void onVideoSizeChanged(com.ss.videoarch.liveplayer2.VeLivePlayer a0, int a1, int a2) {
        try { handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("onVideoSizeChanged", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, int.class, int.class}), new Object[]{a0, a1, a2}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
    @Override public com.ss.videoarch.liveplayer2.VeLivePlayerAbrSwitchRequestResult shouldAutomaticallySwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a2, org.json.JSONObject a3) {
        try { return (com.ss.videoarch.liveplayer2.VeLivePlayerAbrSwitchRequestResult) handler.invoke(this, com.ss.videoarch.liveplayer2.VeLivePlayerObserver.class.getMethod("shouldAutomaticallySwitch", new Class<?>[]{com.ss.videoarch.liveplayer2.VeLivePlayer.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolution.class, com.ss.videoarch.liveplayer2.VeLivePlayerResolution.class, org.json.JSONObject.class}), new Object[]{a0, a1, a2, a3}); } catch (Throwable error) { throw new RuntimeException(error); }
    }
}
