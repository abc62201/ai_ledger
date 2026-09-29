function showReward() {
  let html = `
      <!-- 半透明遮罩层 -->
      <div id="reward-modal"
           style="position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);display:flex;justify-content:center;align-items:center;z-index:9999;"
           onclick="cancel_aiconfig()">

        <div style="background:#fff;border-radius:12px;padding:24px 16px;width:90%;text-align:center;box-shadow:0 10px 30px rgba(0,0,0,0.25);">

          <h2 style="margin:0 0 10px;font-size:18px;color:#333;">请作者喝杯咖啡 ☕</h2>
          <p style="margin:0 0 20px;font-size:14px;color:#777;">如果觉得软件好用，欢迎打赏支持持续更新</p>

          <!-- 👇 上下排列区域 👇 -->
          <div style="display:flex;flex-direction:column;gap:15px;align-items:center;margin-bottom:15px;">
            <!-- 👇 你的收款码填在这里 👇 -->
            <img id="wx-pay" src="wechat_pay.png" alt="微信收款码" style="width:100%;max-width:240px;border:1px solid #eee;border-radius:8px;"
                 >
          </div>
           <div style="display:flex;flex-direction:column;gap:15px;align-items:center;margin-bottom:15px;">
            <img id="ali-pay" src="alipay.png" alt="支付宝收款码" style="width:100%;max-width:240px;border:1px solid #eee;border-radius:8px;"
                 >
            <!-- 👆 你的收款码填在这里 👆 -->
          </div>
          <!-- 👆 上下排列区域 👆 -->
          <p style="margin:0;font-size:12px;color:#aaa;">截屏图片保存，在微信/支付宝中扫码</p>
        </div>
      </div>
  `;

  set_cont.innerHTML = html;


}