// 获取 DOM 元素
const chatBox = document.getElementById('chat-box');
const inputArea = document.getElementById('input-area');
const chatInput = document.getElementById('chat-input');
const toggleBtn = document.getElementById('toggle-mode');
const voiceBtn = document.getElementById('voice-btn');

let isVoiceMode = false;

// 1. 切换输入模式 (键盘 <-> 语音)
function toggleInputMode() {
    isVoiceMode = !isVoiceMode;

    if (isVoiceMode) {
        inputArea.classList.add('voice-mode');
        toggleBtn.innerHTML = '⌨️';
        chatInput.blur();
    } else {
        inputArea.classList.remove('voice-mode');
        toggleBtn.innerHTML = '🎤';
        setTimeout(() => chatInput.focus(), 100);
    }
}

// 2. 发送消息逻辑
function sendMessage() {
    const text = chatInput.value.trim();
    if (!text) return;

    // 添加用户消息到界面
    addMessage(text, 'user');
    chatInput.value = '';

    // 通过 JsBridge 发送给 Java → Java 调用 zipuai 进行 AI 处理
    JsHelper.sendToJava(text);
}

// 辅助函数：添加消息到界面
function addMessage(text, type) {
    const div = document.createElement('div');
    div.className = `message ${type}`;
    div.innerText = text;
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}

// 3. 回车键发送
function handleKeyPress(e) {
    if (e.key === 'Enter') {
        sendMessage();
    }
}

// 4. 语音按钮交互反馈
voiceBtn.addEventListener('touchstart', (e) => {
    e.preventDefault();
    voiceBtn.style.background = '#eef';
    voiceBtn.innerText = "松开 结束";
    // 按住时开始录音
    startVoice();
});

    // 松开手指时触发
    voiceBtn.addEventListener('touchend', (e) => {
        e.preventDefault();
        voiceBtn.style.background = '#fff';
        voiceBtn.innerText = "按住 说话";

        // ✅ 核心修改：延迟 1000 毫秒（1秒）再停止录音
        // 这样你在说完话后手指可以多按1秒，让系统把最后一点声音收进去
        setTimeout(() => {
            stopVoice();
        }, 1000);
    });

// 兼容鼠标事件（PC调试用）
voiceBtn.addEventListener('mousedown', (e) => {
    e.preventDefault();
    voiceBtn.style.background = '#eef';
    voiceBtn.innerText = "松开 结束";
    startVoice();
});

voiceBtn.addEventListener('mouseup', (e) => {
    e.preventDefault();
    voiceBtn.style.background = '#fff';
    voiceBtn.innerText = "按住 说话";
            setTimeout(() => {
                stopVoice();
            }, 1000);
});

// 5. 开始语音识别
function startVoice() {
    try {
        if (window.AndroidBridge) {
            window.AndroidBridge.startVoice();
        } else {
            console.error("AndroidBridge 未找到！");
            addMessage("【错误】AndroidBridge 未就绪，请检查 JsBridge 注册", 'ai');
        }
    } catch (e) {
        console.error("startVoice 异常:", e);
        addMessage("【错误】语音功能启动失败: " + e.message, 'ai');
    }
}

// 6. 停止语音识别
function stopVoice() {
    try {
        if (window.AndroidBridge) {
            window.AndroidBridge.stopVoice();
        } else {
            console.error("AndroidBridge 未找到！");
        }
    } catch (e) {
        console.error("stopVoice 异常:", e);
    }
}

// 7. Java 语音识别完成后回调这个函数，把文字填入输入框
function receiveVoiceText(text) {
    console.log("收到语音识别最终结果: " + text);

    if (!text) return;

    // 1. 添加到聊天界面
    addMessage(text, 'user');

    // 2. 填入输入框（方便用户查看或修改）
    const input = document.getElementById('chat-input');
    if (input) {
        input.value = text;
    }

    // 3. 直接发送给 AI（因为现在收到的都是松开手之后的最终结果）
    JsHelper.sendToJava(text);
}