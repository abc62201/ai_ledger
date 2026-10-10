function set_yearly_summary() {
    let now = new Date();
    let year = now.getFullYear();
    let currentMonth = now.getMonth() + 1;
    let defaultCategory = "电费";

    getAllCategories(year, function(categories) {
        let optionsHTML = '';
        categories.forEach(function(cat) {
            let selected = (cat === defaultCategory) ? ' selected' : '';
            optionsHTML += '<option value="' + cat + '"' + selected + '>' + cat + '</option>';
        });

        set_cont.innerHTML = `
          <div class="yearly-summary-container">
            <div class="pay-header">

              <input class="pay-title" type="month" id="set_time"
                     value="${year}-${String(currentMonth).padStart(2,'0')}"
                     onchange="onYearMonthChange(this.value)"/>
              <select id="category_select" onchange="onCategoryChange()">
                ${optionsHTML}
              </select>
              <label class="pay-title" id="year_label">总支出</label>
            </div>
            <div id="yearly_grid" class="yearly-grid"></div>
          </div>
        `;

        renderYearlyGrid(year);
    });
}

function onYearMonthChange(value) {
    let year = Number(value.substring(0, 4));
    //document.getElementById("year_label").innerText = "分类汇总";
    renderYearlyGrid(year);
}

function onCategoryChange() {
    let value = document.getElementById("set_time").value;
    let year = Number(value.substring(0, 4));
    renderYearlyGrid(year);
}

function getAllCategories(year, callback) {
    // 用 queryFinanceData 从数据库查询去重分类
    // 注意：queryFinanceData 是异步回调的，这里简化处理
    // 实际使用时可以在 JsBridge 加一个 getAllCategories 方法
    // 先硬编码兜底，后续可优化
    let defaultCategories = ['餐饮', '食品', '娱乐', '家电', '家具', '数码', '首饰','办公用品','通讯','交通','服装','日用品','玩具','宠物','其他','医疗','房租','水费','电费', '燃气费','工商银行', '建设银行', '个人', '银行'];
    callback(defaultCategories);
}

// ============================================
// 4. 渲染12个月格子
// ============================================

function renderYearlyGrid(year) {
    let category = document.getElementById('category_select').value;
    let type = '支出';

    // 调用 Java 后端获取12个月数据
    let jsonStr = AndroidBridge.getMonthlyCategoryTotal(year, category, type);
    let monthData = JSON.parse(jsonStr);
    // 找最大值和最小值用于高亮
    let maxVal = 0;
    let minVal = Infinity;
    for (let m = 1; m <= 12; m++) {
        let v = monthData[m] ? monthData[m] : 0;
        if (v > maxVal) maxVal = v;
        if (v < minVal) minVal = v;
    }
    if (minVal === Infinity) minVal = 0;
        let html = '<div class="month-grid">';
        for (let m = 1; m <= 12; m++) {
            let total = monthData[m] !== undefined ? monthData[m] : 0;
            let totalStr = total.toFixed(2);
            let highlightClass = '';
            if (total === maxVal && total > 0) highlightClass = ' max-month';
            if (total === minVal && total > 0 && maxVal !== minVal) highlightClass = ' min-month';

            html +=`<div class="month-cell${highlightClass}"
                    onclick="showMonthDetail('${year}', '${m}', '${category}', '${type}')">
                    <div class="month-num"> ${m}月</div>
                    <div class="month-amount"> ${totalStr}</div>
                    </div>
            `;
        }
        html += '</div>';
        document.getElementById('yearly_grid').innerHTML = html;
}
// ============================================
// 5. 点击某个月 -> 查看详情
//    方案A：拼自然语言文本发给 AI 处理（和你现有 selectDate 逻辑一致）
// ============================================
function showMonthDetail(year, month, category, type) {
    // 拼自然语言查询文本
    let queryText = "查询" + year + "年" + month + "月" + type + "中分类=" + category + "的全部明细";
    addMessage(queryText, 'user');
    JsHelper.sendToJava(queryText);
    closeSettings();

}


