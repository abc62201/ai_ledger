const set_js = document.getElementById('set_js');
const set_cont = document.getElementById('set_cont');

let now = new Date();
let currentYear = now.getFullYear();   // 2026
let currentMonth = now.getMonth() + 1; // 9（getMonth 从 0 开始）
let payData = {};  // 改成空对象，从数据库加载

function openSettings() {
    set_cont.innerHTML = `
        <ul class="set_menu">
            <li onclick="set_pay()">查看支出</li>
            <li onclick="set_income()">查看收入</li>
            <li onclick="set_loan(0)">欠债情况</li>
            <li onclick="set_loan(1)">债权情况</li>
            <li onclick="set_init()">设置</li>
            <li onclick="showReward()">打赏作者</li>
        </ul>`;
    set_js.classList.add('show');
}

function closeSettings() {
    set_js.classList.remove('show');
}

// 2. 新增 set_income，传入 "收入" 参数
function set_income() {
    loadAndRender(currentYear, currentMonth, 1, "收入");
}

// 修改你的 set_pay，补上 type 参数，默认为 "支出"
function set_pay() {
    loadAndRender(currentYear, currentMonth, 1, "支出");
}

// 3. 修改 loadAndRender 和 renderCalendar，增加 type 参数
function loadAndRender(year, month, selectedDay, type) {
    let jsonStr = AndroidBridge.getDailyData(year, month, type);
    payData = JSON.parse(jsonStr);

    let totalAmount = AndroidBridge.getMonthlyTotal(year, month, type);

    // 把 type 传给渲染函数
    renderCalendar(year, month, selectedDay, totalAmount, type);
}

function renderCalendar(year, month, selectedDay = 0, totalAmount = 0, type = "支出") {
    currentYear = year;
    currentMonth = month;

    const daysInMonth = new Date(year, month, 0).getDate();
    let firstDayOfWeek = new Date(year, month - 1, 1).getDay();

    let calendarHTML = '';
    let maxAmount = 0;
    let minAmount = Infinity;
    let currentAmount = 0;

    for (let d in payData) {
        let amt = payData[d];
        if (amt > maxAmount) maxAmount = amt;
        if (amt < minAmount) minAmount = amt;
    }

    // 处理全为 0 时 minAmount 为 Infinity 的显示问题
    if (minAmount === Infinity) minAmount = 0;

    if (selectedDay > 0 && payData[selectedDay]) {
        currentAmount = payData[selectedDay];
    }

    for (let i = 0; i < firstDayOfWeek; i++) {
        calendarHTML += `<div class="day-cell empty"></div>`;
    }

    for (let i = 1; i <= daysInMonth; i++) {
        const amount = payData[i];
        let className = 'day-cell';
        let amountStr = '';

        if (amount !== undefined) {
            amountStr = `<div class="date-amount">${amount.toFixed(2)}</div>`;
            if (amount === maxAmount) className += ' max-amount';
            if (amount === minAmount) className += ' min-amount';
        }

        if (i === selectedDay) {
            className += ' active';
        }

        calendarHTML += `
            <div class="${className}" onclick="selectDate(${i}, '${type}')">
                <div class="date-num">${i}</div>
                ${amountStr}
            </div>`;
    }

    // 4. 使用 type 动态修改文案
    set_cont.innerHTML = `
        <div class="pay-view-container">
            <div class="pay-header">
                <label class="pay-title" style="margin-right: 10px;">${currentYear}年${currentMonth}月 (${type})</label>
                <input class="pay-title" type="month" id="set_time"
                       value="${currentYear}-${String(currentMonth).padStart(2,'0')}"/>
            </div>

            <div class="calendar-grid">
                <div class="week-row"><span>日</span><span>一</span><span>二</span><span>三</span><span>四</span><span>五</span><span>六</span></div>
                <div class="days-grid">
                    ${calendarHTML}
                </div>
            </div>

            <div class="summary-box">
                <div class="summary-item">
                    <span class="icon"></span>
                    <span class="label">本月累计${type}(元)</span>
                    <span class="value">${totalAmount.toFixed(2)}</span>
                </div>
                <div class="summary-item selected">
                    <span class="date-label">${currentMonth}月${selectedDay}日${type}</span>
                    <span class="value highlight">${currentAmount.toFixed(2)}</span>
                </div>
            </div>
        </div>
    `;

    document.getElementById('set_time').addEventListener('change', function() {
        let dateStr = this.value;
        if (dateStr) {
            let parts = dateStr.split('-');
            loadAndRender(parseInt(parts[0]), parseInt(parts[1]), 1, type);
        }
    });
}

// 5. 修改 selectDate，确保点击日历时保留 type
function selectDate(day, type) {
    // 构造自然语言查询字符串
    let queryText = `查询${currentYear}年${currentMonth}月${day}日全部${type}`;
addMessage(queryText, 'user');
    // 直接调用 finishAiStreaming，让 AI 去处理
    JsHelper.sendToJava(queryText);
    closeSettings();
}

function set_init(){
    // 1. 拿到 Java 返回的 JSON 字符串
    let ai_config_str = AndroidBridge.ai_data();

    // 2. 转成 JS 对象
    let ai_config_data = JSON.parse(ai_config_str);

    // 3. 现在可以正常取 .key / .url / .model 了
    set_cont.innerHTML = `
     <div class="finance-card fin-edit-container">
        <div class="fin-edit-row">
         <label>ai_key：填写私钥</label>
         <input type="password" id="ai_key" value="${ai_config_data.key}" class="fin-edit-input">
        </div>
        <div class="fin-edit-row">
         <label>ai_url：填写接口地址</label>
         <input type="text" id="ai_url" value="${ai_config_data.url}" class="fin-edit-input">
        </div>
        <div class="fin-edit-row">
         <label>ai_model：填写模型名称</label>
         <input type="text" id="ai_model" value="${ai_config_data.model}" class="fin-edit-input">
        </div>
        <div class="card-actions">
        <button class="btn-cancel" onclick="cancel_aiconfig()">取消</button>
        <button class="btn-confirm" onclick="save_aiconfig()">保存</button>
        </div>
     </div>
    `;
}

// 前端保存配置
function save_aiconfig() {
    let key = document.getElementById('ai_key').value;
    let url = document.getElementById('ai_url').value;
    let model = document.getElementById('ai_model').value;

    // 打包成 JSON 字符串，直接传给 Java 端
    let config_str = JSON.stringify({
        "key": key,
        "url": url,
        "model": model
    });

    // 调用 Java 端的保存方法
   AndroidBridge.save_ai_data(config_str);
   closeSettings();
}

// 取消按钮逻辑（比如关闭弹窗）
function cancel_aiconfig() {
    // 这里可以写你的关闭弹窗逻辑
    openSettings();
}