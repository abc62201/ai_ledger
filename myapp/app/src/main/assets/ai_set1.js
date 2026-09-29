function set_loan(type) {
    // type=0 查"贷款/还款"，type=1 查"借出/收债"
    let loan = AndroidBridge.getloanData(type);
    let loanData = JSON.parse(loan);
    debit_show(loanData);
}
function debit_show(loanData) {
    let html = '';
    let totalCount = 0;
    let totalAmount = 0;

    loanData.forEach(group => {
        let groupCount = group.loan_table.length;
        let groupSum = group.loan_table.reduce((sum, item) => sum + item.total_price, 0);
        totalCount += groupCount;
        totalAmount += groupSum;

        // 分组大卡片容器
        html += `<div style="background:#fff;border-radius:12px;margin-bottom:16px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">`;

        // 分组标题（贷款/还款）
        html += `<div style="background:linear-gradient(135deg,#667eea,#764ba2);color:#fff;padding:14px 16px;font-size:16px;font-weight:bold;">${group.income_expense}</div>`;

        // 格子容器（自动换行的 flex 布局，手机上自适应）
        html += `<div style="display:flex;flex-wrap:wrap;gap:10px;padding:12px;">`;


        group.loan_table.forEach(item => {
            // 每个小格子
            html += `
                <div style="background:#fafafa;border-radius:8px;padding:12px;text-align:center;border:1px solid #eee;flex:1 1 140px;min-width:140px;" onclick="select_debit('${group.income_expense}', '${item.category}','${item.label}')">
                    <div style="font-size:14px;color:#333;font-weight:500;">${item.category}</div>
                    <div style="font-size:12px;color:#999;margin:4px 0;">${item.label}</div>
                    <div style="font-size:16px;color:#e74c3c;font-weight:bold;margin-top:6px;">¥${item.total_price.toFixed(2)}</div>
                </div>`;
        });


        html += `</div>`;

        // 分组底部统计
        html += `<div style="display:flex;justify-content:space-between;padding:10px 16px;background:#f9f9f9;font-size:13px;color:#666;border-top:1px solid #eee;">
            <span>共 ${groupCount} 条</span>
            <span>合计 ¥${groupSum.toFixed(2)}</span>
        </div>`;

        html += `</div>`;
    });

    set_cont.innerHTML = html;
}

function select_debit(income_expense, category,label) {
    // 构造自然语言查询字符串
    let queryText = `查询${income_expense}中分类=${category},标签=${label}`;
	addMessage(queryText, 'user');
    // 直接调用 finishAiStreaming，让 AI 去处理
    JsHelper.sendToJava(queryText);
    closeSettings();
}