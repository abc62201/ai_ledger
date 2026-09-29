package com.ai.ledger;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognizerIntent;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ai.ledger.JsBridge;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;
    private VoiceHelper voiceHelper;
    private zipuai zipuai;
    // 定义一个请求码，用来标识这是“录音权限”的请求
    private static final int PERMISSION_REQUEST_CODE = 1001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // 还没有权限，弹窗请求
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_CODE);
        } else {
            // 已经有权限了，直接初始化语音
            initVoice();
        }
        // 1. 找到 XML 里定义的 WebView
        myWebView = (WebView) findViewById(R.id.web_c);

        // 2. 获取设置对象
        WebSettings webSettings = myWebView.getSettings();

        // 3. 开启 JavaScript
        webSettings.setJavaScriptEnabled(true);
        // 提前触发数据库初始化
        DbHelper.getInstance(this).getWritableDatabase();
        // 关键：把 JsBridge 实例注册到 WebView
        myWebView.addJavascriptInterface(new JsBridge(myWebView, this), "AndroidBridge");

        // 4. 让网页在 App 内部打开
        myWebView.setWebViewClient(new WebViewClient());

        // 5. 开启远程调试
        WebView.setWebContentsDebuggingEnabled(true);
        // 开启 DOM 存储（localStorage / sessionStorage），帮助 WebView 保存页面状态
        webSettings.setDomStorageEnabled(true);

        // 6. 加载本地网页
        myWebView.loadUrl("file:///android_asset/index.html");


        zipuai.init(this);
        //拷贝出数据库文件
        //copyDatabaseToPublic();
        // 【新增】初始化语音助手
        this.voiceHelper = new VoiceHelper(new VoiceHelper.OnResultListener() {
            @Override
            public void onResult(String text) {
                // 这里就是拿到语音识别文字的地方！
                // 比如你可以把文字打印出来，或者传给 WebView
                Log.d("MainActivity", "收到语音: " + text);

                // 如果你想传给前端 JS，可以调用 JsBridge 的方法（如果有的话）
                // jsBridge.receiveData(text);
            }
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 用户点了“允许”
                Toast.makeText(this, "录音权限已开启", Toast.LENGTH_SHORT).show();
                initVoice();
            } else {
                // 用户点了“拒绝”
                Toast.makeText(this, "需要录音权限才能使用语音功能", Toast.LENGTH_LONG).show();
            }
        }
    }
    // ================================================

    // ========== 【新增】单独提取出来的语音初始化方法 ==========
    private void initVoice() {
        this.voiceHelper = new VoiceHelper(new VoiceHelper.OnResultListener() {
            @Override
            public void onResult(String text) {
                Log.d("MainActivity", "收到语音: " + text);
            }
        });
    }


    // ========== 补充的生命周期方法 ==========

    @Override
    protected void onPause() {
        super.onPause();
        if (myWebView != null) {
            // 暂停 WebView 的所有 JS 定时器、DOM 解析等，降低 CPU 占用
            myWebView.onPause();
            myWebView.pauseTimers();
        }
        if (voiceHelper != null) {
            // 如果语音助手有暂停方法，也在这里暂停
            // voiceHelper.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myWebView != null) {
            // 恢复 WebView 的所有功能
            myWebView.onResume();
            myWebView.resumeTimers();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (myWebView != null) {
            // 保存 WebView 的当前状态（页面内容、滚动位置等）
            myWebView.saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (myWebView != null && savedInstanceState != null) {
            // 恢复之前保存的 WebView 状态
            myWebView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myWebView != null) {
            // 1. 先从父容器中移除
            ViewParent parent = myWebView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(myWebView);
            }
            // 2. 停止加载、清除监听器
            myWebView.stopLoading();
            myWebView.setWebViewClient(null);
            myWebView.setWebChromeClient(null);
            // 3. 加载空白页释放资源
            myWebView.loadUrl("about:blank");
            myWebView.removeAllViews();
            // 4. 销毁
            myWebView.destroy();
            myWebView = null;
        }
        if (voiceHelper != null) {
            // voiceHelper.release(); // 如果语音助手有释放方法
        }
    }
    /**
     * 将数据库文件复制到手机公共下载目录，方便在电脑上查看
     */
    private void copyDatabaseToPublic() {
        try {
            // 1. 找到源文件
            File dbFile = new File("/data/data/com.ai.ledger/databases/yin_finance.db");

            // 2. 目标路径：手机的 Download 文件夹
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File destFile = new File(downloadDir, "yin_finance.db");

            // 3. 复制文件
            java.nio.file.Files.copy(dbFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Log.d("MainActivity", "✅ 数据库已成功复制到: " + destFile.getAbsolutePath());
            Toast.makeText(this, "数据库已复制到 Download 文件夹！", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e("MainActivity", "❌ 复制失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

}