package com.ai.ledger;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class VoiceHelper {
    private static final String TAG = "VoiceHelper";
    //vosk语音地址。地址-去掉http填到下面的ws://后面
    private static final String VOSK_URL = "ws://";
    private static final int SAMPLE_RATE = 16000; // 必须与Vosk服务一致
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = 8000; // 每次发送的采样点数，约100ms音频
    private OnResultListener listener;
    private OkHttpClient client;
    private WebSocket webSocket;
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;

    // 回调接口

    public VoiceHelper(OnResultListener listener) {
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .build();
    }

    /**
     * 连接到Vosk服务器并开始录音识别
     */
    public void startVoice() {
        if (webSocket != null) {
            Log.d(TAG, "已经处于连接状态，无需重复启动");
            return;
        }

        Request request = new Request.Builder()
                .url(VOSK_URL)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                super.onOpen(ws, response);
                Log.d(TAG, "✅ WebSocket 连接成功");

                // 连接成功后，立即发送初始化命令
                ws.send("{\"config\": {\"sample_rate\": " + SAMPLE_RATE + "}}");

                // 开始录音
                startRecording(ws);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                super.onMessage(ws, text);
                Log.d(TAG, "收到文本消息: " + text);

                // 在主线程回调UI更新
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onResult(text));
                }
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                super.onMessage(ws, bytes);
                // Vosk通常不会发送二进制消息，忽略即可
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                super.onClosing(ws, code, reason);
                Log.d(TAG, "正在关闭连接: " + reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                super.onClosed(ws, code, reason);
                Log.d(TAG, "连接已关闭: " + reason);
                stopRecording();
                webSocket = null;
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                super.onFailure(ws, t, response);
                Log.e(TAG, "连接失败", t);

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            listener.onResult("{\"error\": \"" + t.getMessage() + "\"}"));
                }
                stopRecording();
                webSocket = null;
            }
        });
    }

    /**
     * 开始录音并发送音频数据
     */
    private void startRecording(WebSocket ws) {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "无效的 AudioRecord 参数");
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败");
                return;
            }

            audioRecord.startRecording();
            isRecording = true;

            recordingThread = new Thread(() -> {
                short[] buffer = new short[BUFFER_SIZE];
                while (isRecording) {
                    int readSize = audioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (readSize > 0 && ws != null) {
                        // 将 short[] 转换为 byte[] 发送
                        byte[] pcmData = new byte[readSize * 2];
                        for (int i = 0; i < readSize; i++) {
                            pcmData[i * 2] = (byte) (buffer[i] & 0xFF);
                            pcmData[i * 2 + 1] = (byte) ((buffer[i] >> 8) & 0xFF);
                        }
                        ws.send(ByteString.of(pcmData));
                    } else if (readSize < 0) {
                        Log.e(TAG, "音频读取错误: " + readSize);
                        break;
                    }
                }
            });
            recordingThread.start();
            Log.d(TAG, "🎙️ 开始录音并发送音频数据...");

        } catch (SecurityException e) {
            Log.e(TAG, "缺少录音权限: " + e.getMessage());
            if (listener != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                        listener.onResult("{\"error\": \"缺少录音权限\"}"));
            }
        }
    }

    /**
     * 停止录音并关闭连接
     */
    public void stopVoice() {
        // 1. 发送 eof 告诉服务器录音结束
        if (webSocket != null) {
            webSocket.send("{\"eof\": 1}");
        }

        // 2. 立刻停止本地录音，释放麦克风
        stopRecording();

        // 3. 延迟 2 秒后，彻底关闭 WebSocket 连接
        // 给服务器 2 秒的时间去处理最后的声音并返回最终的 text
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (webSocket != null) {
                webSocket.close(1000, "正常结束");
                Log.d(TAG, "🛑 强制关闭 WebSocket 连接");
            }
        }, 500);
    }

    private void stopRecording() {
        isRecording = false;
        if (recordingThread != null && recordingThread.isAlive()) {
            recordingThread.interrupt();
        }
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    // 回调接口定义
    public interface OnResultListener {
        void onResult(String result);
    }
}