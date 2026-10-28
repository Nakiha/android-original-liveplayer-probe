// Signature-only compile contract; no original implementation. Not packaged.
package com.ss.videoarch.liveplayer2;
public interface VeLivePlayerObserver {
    void didAutomaticallySwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a2, org.json.JSONObject a3);
    void onAudioDeviceClose(com.ss.videoarch.liveplayer2.VeLivePlayer a0);
    void onAudioDeviceOpen(com.ss.videoarch.liveplayer2.VeLivePlayer a0, int a1, int a2, int a3);
    void onAudioDeviceRelease(com.ss.videoarch.liveplayer2.VeLivePlayer a0);
    void onAudioFrameRenderMetaInfo(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioFrameRenderMetaInfo a1);
    void onAudioRenderStall(com.ss.videoarch.liveplayer2.VeLivePlayer a0, long a1);
    void onAudioVolume(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioVolume a1);
    void onDrawFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, android.hardware.HardwareBuffer a1, int[] a2, int a3, int a4);
    void onError(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerError a1);
    void onFirstAudioFrameRender(com.ss.videoarch.liveplayer2.VeLivePlayer a0, boolean a1);
    void onFirstVideoFrameRender(com.ss.videoarch.liveplayer2.VeLivePlayer a0, boolean a1);
    void onHeadPoseUpdate(com.ss.videoarch.liveplayer2.VeLivePlayer a0, float a1, float a2, float a3, float a4, float a5, float a6, float a7);
    void onLoudness(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioLoudnessInfo a1);
    void onMainBackupSwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerStreamType a1, com.ss.videoarch.liveplayer2.VeLivePlayerError a2);
    void onMonitorLog(com.ss.videoarch.liveplayer2.VeLivePlayer a0, java.lang.String a1, java.lang.String a2);
    java.lang.String onMuteIssueWarning(com.ss.videoarch.liveplayer2.VeLivePlayer a0);
    void onNetworkQualityChanged(com.ss.videoarch.liveplayer2.VeLivePlayer a0, int a1, java.lang.String a2);
    void onPlayerStatusUpdate(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerStatus a1);
    void onReceiveBinarySeiMessage(com.ss.videoarch.liveplayer2.VeLivePlayer a0, java.nio.ByteBuffer a1);
    void onReceiveSeiMessage(com.ss.videoarch.liveplayer2.VeLivePlayer a0, java.lang.String a1);
    void onRenderAudioFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerAudioFrame a1);
    void onRenderVideoFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerVideoFrame a1);
    void onResolutionDegrade(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1);
    void onResolutionSwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1, com.ss.videoarch.liveplayer2.VeLivePlayerError a2, com.ss.videoarch.liveplayer2.VeLivePlayerResolutionSwitchReason a3);
    void onSnapshotComplete(com.ss.videoarch.liveplayer2.VeLivePlayer a0, android.graphics.Bitmap a1);
    void onStallEnd(com.ss.videoarch.liveplayer2.VeLivePlayer a0);
    void onStallStart(com.ss.videoarch.liveplayer2.VeLivePlayer a0);
    void onStatistics(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerStatistics a1);
    void onStreamFailedOpenSuperResolution(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerError a1);
    void onTextureRenderDrawFrame(com.ss.videoarch.liveplayer2.VeLivePlayer a0, android.view.Surface a1);
    void onVideoFrameRenderMetaInfo(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerVideoFrameRenderMetaInfo a1);
    void onVideoRenderStall(com.ss.videoarch.liveplayer2.VeLivePlayer a0, long a1);
    void onVideoSizeChanged(com.ss.videoarch.liveplayer2.VeLivePlayer a0, int a1, int a2);
    com.ss.videoarch.liveplayer2.VeLivePlayerAbrSwitchRequestResult shouldAutomaticallySwitch(com.ss.videoarch.liveplayer2.VeLivePlayer a0, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a1, com.ss.videoarch.liveplayer2.VeLivePlayerResolution a2, org.json.JSONObject a3);
}
