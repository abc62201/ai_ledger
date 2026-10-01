class JsHelper {

    // 向 Java 发送文本
    static sendToJava(text) {
        if (window.AndroidBridge) {
            window.AndroidBridge.receiveData(text);
        } else {
            console.log("AndroidBridge 未找到，当前在浏览器环境");
        }
    }

    // 向 Java 发送 JSON 数据
    static sendJsonToJava(json) {
        if (window.AndroidBridge) {
            window.AndroidBridge.confirmFinanceRecord(json);
        } else {
            console.log("AndroidBridge 未找到，当前在浏览器环境");
        }
    }

    // Java 调用：往页面添加 AI 消息
    static addAiMessage(text) {
        const chatBox = document.getElementById('chat-box');
        const div = document.createElement('div');
        div.className = 'message ai';
        div.innerText = text;
        chatBox.appendChild(div);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    // Java 调用：往页面添加用户消息
    static addUserMessage(text) {
        const chatBox = document.getElementById('chat-box');
        const div = document.createElement('div');
        div.className = 'message user';
        div.innerText = text;
        chatBox.appendChild(div);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    // ============================================================
    // 流式 AI 响应处理方法（由 JsBridge 通过 evaluateJavascript 调用）
    // ============================================================

    static _aiStreamDiv = null;
    static _currentFinanceData = null;    // 暂存当前账单数据
    static _originalFinanceData = null;   // 编辑前的原始数据快照（用于对比）
    static _originalCardHtml = null;      // 编辑前的原始卡片 HTML（取消编辑时恢复）

    static startAiStreaming() {
        const chatBox = document.getElementById('chat-box');
        const div = document.createElement('div');
        div.className = 'message ai streaming';
        div.innerText = '...';
        chatBox.appendChild(div);
        chatBox.scrollTop = chatBox.scrollHeight;
        this._aiStreamDiv = div;
    }

    static appendAiChunk(chunk) {
        if (this._aiStreamDiv) {
            this._aiStreamDiv.innerText += chunk;
            const chatBox = document.getElementById('chat-box');
            chatBox.scrollTop = chatBox.scrollHeight;
        }
    }

    /**
     * AI 流式结束 —— 解析 JSON，渲染记账确认卡片
     */
    static finishAiStreaming(text) {
        if (this._aiStreamDiv) {
            this._aiStreamDiv.classList.remove('streaming');

            try {
                const data = JSON.parse(text);

                if (!data.success) {
                    this._aiStreamDiv.innerText = '❌ ' + (data.error || '解析失败');
                    this._aiStreamDiv = null;
                    return;
                }
                switch(data.intent){
                case "记账":
                this._renderFinanceCard(data);
                break;
                case "查询":
                const sqlCondition = data.sql_condition;
                this._aiStreamDiv.innerText = "正在查询数据库...";
                window.AndroidBridge.queryFinanceData(sqlCondition);
                break;
                case "闲聊":
                this._aiStreamDiv.innerText = data.greeting+","+data.content;
                break;
                }



            } catch (e) {
                this._aiStreamDiv.innerText = text;
            }
        }
    }













          static onQueryResult(text) {
              console.log("收到原生查询结果:", text);

              try {
                  const data = JSON.parse(text);

                  // 清空原有内容
                  this._aiStreamDiv.innerHTML = '';

                  if (data.row_table && data.row_table.length > 0) {
                      // 生成一个唯一的 ID 用于绑定折叠事件
                      const uniqueId = 'group_' + new Date().getTime();

                      // 1. 使用 DOM API 创建一个包裹容器
                      const containerDiv = document.createElement('div');
                      //标签配色
                      const income_expense = document.createElement('div');
                      const val = data.row_table[0].income_expense;
                      income_expense.innerHTML = val;
                      // 居中 + 字号加大 + 根据内容自动变色
                      income_expense.style.fontSize = '16pt';
                      income_expense.style.fontWeight = 'bold';
                      if (val.includes('支出')) {
                         income_expense.style.color = '#dc2626';  // 红色
                      } else if (val.includes('收入')) {
                         income_expense.style.color = '#16a34a';  // 绿色
                      }
                      containerDiv.appendChild(income_expense);

                      // 2. 遍历数据生成卡片
                      data.row_table.forEach((item, index) => {
                          const card = document.createElement('div');
                          card.className = `detail-card-${uniqueId}`; // 绑定唯一 ID 的类名
                        // 设置第一条卡片的特殊样式，其他卡片保持基础样式
                          if (index === 0) {
                              card.style.cssText = "background:#fff; border-radius:10px; padding:15px; margin-bottom:10px; border:1px solid #eee; box-shadow: 0 2px 4px rgba(0,0,0,0.05);";
                          } else {
                              card.style.cssText = "background:#fff; border-radius:10px; padding:15px; margin-bottom:10px; border:1px solid #eee; box-shadow: 0 2px 4px rgba(0,0,0,0.05);";
                          }
                          // 核心逻辑：第一条(index 0)默认显示，其余全部隐藏
                          if (index > 0) {
                              card.style.display = 'none';
                          }



                          // 构建卡片内部 HTML (只拼接数据，不拼接变量逻辑，极其安全)
                          card.innerHTML = `
                              <div style="display:flex; justify-content:space-between; font-weight:bold; margin-bottom:6px;">
                                  <span style="font-size:16px;">${item.label}</span>
                                  <span>¥${item.total_price}</span>
                              </div>
                              <div style="font-size:12px; color:#666; display:flex; justify-content:space-between;">
                                  <span>${item.category}</span>
                                  <span>数量: ${item.quantity}</span>
                                  <span>${item.timestamp}</span>
                              </div>
                             <div style="margin-top: 8px;">
                                 <span style="background: #e0e0e0; color: #333; padding: 4px 10px; border-radius: 15px; font-size: 13px;">
                                     💬 ${item.raw_text}
                                 </span>
                             </div>
                          `;
                          containerDiv.appendChild(card);
                      });

                      // 3. 创建展开/折叠按钮
                      const arrowBtn = document.createElement('div');
                      arrowBtn.id = `arrow-${uniqueId}`;
                      arrowBtn.innerHTML = '⬇️'; // 初始状态显示向下箭头
                      arrowBtn.style.cssText = "text-align:center; padding: 10px 0; cursor: pointer; font-size: 26px; color: #555;";

                      // 4. 使用原生事件监听器绑定点击事件 (绝对不会失效！)
                      arrowBtn.addEventListener('click', function() {
                          const allCards = document.querySelectorAll(`.detail-card-${uniqueId}`);
                          if (allCards.length === 0) return;

                          // 检查第二条数据(index 1)的当前状态
                          const isHidden = allCards[1].style.display === 'none';

                          allCards.forEach((card, index) => {
                              // 永远保持第一条(index 0)不动，只切换其他的
                              if (index > 0) {
                                  card.style.display = isHidden ? 'block' : 'none';
                              }
                          });

                          // 切换箭头方向
                          this.innerHTML = isHidden ? '⬆️' : '⬇️';
                      });

                      // 将按钮追加到容器后面
                      containerDiv.appendChild(arrowBtn);
                      this._aiStreamDiv.appendChild(containerDiv);

                  } else {
                      this._aiStreamDiv.innerHTML = '<div style="text-align:center; color:#999; padding:20px;">未找到相关记录</div>';
                      return;
                  }

                  // 5. 渲染汇总数据
                  const totalDiv = document.createElement('div');
                  totalDiv.style.cssText = "text-align:center; font-size:14px; color:#555; margin-top:8px; padding-bottom: 15px;";
                  totalDiv.innerHTML = `总计: ${data.row_table.length} 笔 | 总金额: ¥${Number(data.sumTotalPrice).toFixed(2)} | 总数量: ${data.sumQuantity}`;
                  this._aiStreamDiv.appendChild(totalDiv);

              } catch (e) {
                  console.error("JSON解析失败:", e);
                  this._aiStreamDiv.innerText = "数据解析出错: " + text;
              }
          }









    /**
     * 渲染记账确认卡片（展示态）
     */
 static _renderFinanceCard(data) {
        this._currentFinanceData = data;

        const type = data.income_expense; // 获取收支字段：收入/支出/贷款/还款
        const tagInfo = this._getTypeTag(type);
        // 金额显示逻辑：钱进我口袋 → +，钱出我口袋 → -
        const moneyInTypes = ['收入', '贷款', '收债'];
        const sign = moneyInTypes.includes(type) ? '+' : '-';

        const cardHtml = `
            <div class="finance-card">
                <div class="card-header">
                    <span class="card-time">📅 ${data.timestamp}</span>
                    <span class="card-category">${data.category}</span>
                </div>
                <div class="card-body">
                    <div class="card-info">
                        <h4>${data.label}</h4>
                        <p>${data.quantity}个 × ${data.unit_price}元</p>
                    </div>
                    <div class="card-right">
                        <!-- 1. 新增：业务类型标签 -->
                        <div class="type-tag" style="background: ${tagInfo.bgColor}; color: ${tagInfo.textColor};">
                            ${tagInfo.text}
                        </div>
                        <!-- 2. 显示金额 -->
                        <div class="card-price ${tagInfo.priceClass}">
                            ${sign}${parseFloat(data.total_price).toFixed(2)}
                        </div>
                    </div>
                </div>
                <div class="card-actions">
                    <button class="btn-cancel" onclick="JsHelper.cancelRecord()">修改</button>
                    <button class="btn-confirm" onclick="JsHelper.confirmRecord()">确认记账</button>
                </div>
            </div>
        `;

        this._aiStreamDiv.innerHTML = cardHtml;
        // 【关键修改】不再设为 null，保持引用，供后续修改/确认使用
        // this._aiStreamDiv = null;  // 已删除此行

        const chatBox = document.getElementById('chat-box');
        chatBox.scrollTop = chatBox.scrollHeight;
    }


 // 新增辅助方法：处理四种状态的UI样式
    static _getTypeTag(type) {
        switch (type) {
            case '收入':
                return {
                    text: '收入',
                    bgColor: '#ecfdf5', // 浅绿背景
                    textColor: '#059669', // 绿字
                    priceClass: 'income' // 对应你原本的收入颜色样式
                };
            case '支出':
                return {
                    text: '支出',
                    bgColor: '#fef2f2', // 浅红背景
                    textColor: '#dc2626', // 红字
                    priceClass: 'expense'
                };
            case '贷款':
                return {
                    text: '贷款',
                    bgColor: '#eff6ff', // 浅蓝背景
                    textColor: '#2563eb', // 蓝字
                    priceClass: 'expense' // 贷款进账虽然也是+，但颜色可以用蓝色区分，或者沿用支出色
                };
            case '还款':
                return {
                    text: '还款',
                    bgColor: '#fef2f2',
                    textColor: '#dc2626',
                    priceClass: 'expense'
                };
                case '借出':
                return {
                    text: '借出',
                    bgColor: '#f5f3ff',
                    textColor: '#7c3aed',
                    priceClass: 'expense'
            };
                    case '收债':
                        return {
                            text: '收债',
                            bgColor: '#ede9fe',
                            textColor: '#6d28d9',
                            priceClass: 'income'
                        };
            default:
                return {
                    text: '未知',
                    bgColor: '#f5f5f5',
                    textColor: '#333',
                    priceClass: ''
                };
        }
    }








    /**
     * 用户点击"修改" —— 卡片原地变身编辑态
     * 保留原始记录快照（灰色），方便对比
     */
        /**
         * 用户点击"修改" —— 卡片原地变身编辑态
         * 保留原始记录快照（灰色），方便对比
         */

      /**
       * 用户点击"修改" —— 卡片原地变身编辑态
       * 保留原始记录快照（灰色），方便对比
       */
      static cancelRecord() {
          if (!this._aiStreamDiv || !this._currentFinanceData) return;

          const data = this._currentFinanceData;
          const type = data.income_expense; // 获取当前收支类型
          const sign = type === '收入' ? '+' : '-';

          // 保存原始数据快照
          this._originalFinanceData = { ...data };

          // 预设分类下拉菜单选项
          const categories = ['餐饮', '食品', '娱乐', '家电', '家具', '数码', '首饰','办公用品','通讯','交通','服装','日用品','玩具','宠物','其他','医疗','房租','水费','电费', '燃气费','工商银行', '建设银行', '个人', '银行'];
          const categoryOptions = categories.map(c =>
              `<option value="${c}" ${data.category === c ? 'selected' : ''}>${c}</option>`
          ).join('');

          // 新增：收支类型下拉菜单选项（4种状态）
          const incomeExpenseTypes = ['收入', '支出', '贷款', '还款', '借出', '收债'];
          const typeOptions = incomeExpenseTypes.map(t =>
              `<option value="${t}" ${data.income_expense === t ? 'selected' : ''}>${t}</option>`
          ).join('');

          // 日期处理逻辑
          let tempDate = data.timestamp;
          if (tempDate) {
              if (typeof tempDate !== 'string') {
                  tempDate = new Date(tempDate).toISOString().slice(0, 10);
              } else {
                  tempDate = tempDate.substring(0, 10);
              }
          } else {
              tempDate = new Date().toISOString().slice(0, 10);
          }
          data.timestamp = tempDate;

          // 构建编辑态 HTML
          const editHtml = `
              <!-- 原始记录快照 -->
              <div class="finance-card record-snapshot">
                  <div class="card-header">
                      <span>📋 原始记录</span>
                  </div>
                  <div class="card-body">
                      <div class="card-info">
                          <h4>${data.label}</h4>
                          <p>${data.quantity}个 × ${data.unit_price}元</p>
                      </div>
                      <div class="card-price">
                          ${sign}${parseFloat(data.total_price).toFixed(2)}
                      </div>
                  </div>
              </div>

              <!-- 编辑表单 -->
              <div class="finance-card fin-edit-container">
                  <div class="card-header">
                      <span style="font-size:1.2em; margin-right:5px;">✏️</span> 编辑记录
                  </div>

                  <div class="fin-edit-body">
                      <!-- 1. 日期 (独占一行) -->
                      <div class="fin-edit-row">
                          <label>日期：</label>
                          <input type="date" id="edit-timestamp" value="${data.timestamp}" class="fin-edit-input">
                      </div>

                      <!-- 2. 收支类型 (独占一行，新增) -->
                      <div class="fin-edit-row">
                          <label>类型：</label>
                          <select id="edit-income_expense" class="fin-edit-select">
                              ${typeOptions}
                          </select>
                      </div>

                      <!-- 3. 分类 (独占一行) -->
                      <div class="fin-edit-row">
                          <label>分类：</label>
                          <select id="edit-category" class="fin-edit-select">
                              ${categoryOptions}
                          </select>
                      </div>

                      <!-- 4. 名称 (独占一行) -->
                      <div class="fin-edit-row">
                          <label>名称：</label>
                          <input type="text" id="edit-label" value="${data.label}" class="fin-edit-input">
                      </div>

                      <!-- 5. 数量与单价 (一行两个) -->
                      <div class="fin-edit-row-group">
                          <div class="fin-edit-half">
                              <label>数量：</label>
                              <input type="number" id="edit-quantity" value="${data.quantity}" class="fin-edit-input">
                          </div>
                          <div class="fin-edit-half">
                              <label>单价：</label>
                              <input type="number" id="edit-unit_price" value="${data.unit_price}" step="0.01" class="fin-edit-input">
                          </div>
                      </div>
                  </div>

                  <div class="card-actions">
                      <button class="btn-cancel" onclick="JsHelper.cancelEdit()">取消</button>
                      <button class="btn-confirm" onclick="JsHelper.saveRecord()">保存修改</button>
                  </div>
              </div>
          `;

          this._originalCardHtml = this._aiStreamDiv.innerHTML;
          this._aiStreamDiv.innerHTML = editHtml;
      }

      /**
       * 用户点击"保存修改" —— 更新数据，保留原始记录 + 显示新卡片
       */
      static saveRecord() {
          if (!this._aiStreamDiv || !this._currentFinanceData) return;

          // 获取新值
          const label = document.getElementById('edit-label').value || '未命名';
          const quantity = parseFloat(document.getElementById('edit-quantity').value) || 0;
          const unit_price = parseFloat(document.getElementById('edit-unit_price').value) || 0;
          const timestamp = document.getElementById('edit-timestamp').value;
          const category = document.getElementById('edit-category').value;
          // 新增：获取收支类型
          const income_expense = document.getElementById('edit-income_expense').value;

          // 自动计算总价，防止算错
          const total_price = (quantity * unit_price).toFixed(2);

          // 更新内存中的数据
          this._currentFinanceData.label = label;
          this._currentFinanceData.quantity = quantity;
          this._currentFinanceData.unit_price = unit_price;
          this._currentFinanceData.total_price = total_price;
          this._currentFinanceData.timestamp = timestamp;
          this._currentFinanceData.category = category;
          this._currentFinanceData.income_expense = income_expense; // 新增：保存收支类型

          const newData = this._currentFinanceData;
          const oldData = this._originalFinanceData;
          const type = newData.income_expense;
          const moneyInTypes = ['收入', '贷款', '收债'];
          const sign = moneyInTypes.includes(type) ? '+' : '-';
          const tagInfo = this._getTypeTag(type);
          // 构建：原始记录快照 + 更新后的卡片
          const savedHtml = `
              <!-- 原始记录快照 -->
              <div class="finance-card record-snapshot">
                  <div class="card-header"><span>📋 原始记录</span></div>
                  <div class="card-body">
                      <div class="card-info">
                          <h4>${oldData.label}</h4>
                          <p>${oldData.quantity}个 × ${oldData.unit_price}元</p>
                      </div>
                      <div class="card-price">${sign}${parseFloat(oldData.total_price).toFixed(2)}</div>
                  </div>
              </div>

              <!-- 更新后的卡片 -->
              <div class="finance-card">
                  <div class="card-header">
                      <span>📅 ${newData.timestamp}</span>
                      <span>${newData.category}</span>
                  </div>
                  <div class="card-body">
                      <div class="card-info">
                          <h4>${newData.label}</h4>
                          <p>${newData.quantity}个 × ${newData.unit_price}元</p>
                      </div>
                      <div class="card-right">
                   <!-- 1. 新增：业务类型标签 -->
                   <div class="type-tag" style="background: ${tagInfo.bgColor}; color: ${tagInfo.textColor};">
                   ${tagInfo.text}
                    </div>
                          <div class="card-price">
                              ${sign}${parseFloat(newData.total_price).toFixed(2)}
                          </div>
                      </div>
                  </div>
                  <div class="card-actions">
                      <button class="btn-cancel" onclick="JsHelper.cancelRecord()">修改</button>
                      <button class="btn-confirm" onclick="JsHelper.confirmRecord()">确认记账</button>
                  </div>
              </div>
          `;

          this._aiStreamDiv.innerHTML = savedHtml;
      }









        // 确保你有这个取消函数，如果没有请加上
        static cancelEdit() {
             if (this._originalCardHtml && this._aiStreamDiv) {
                 this._aiStreamDiv.innerHTML = this._originalCardHtml;
                 this._currentFinanceData = this._originalFinanceData; // 恢复数据
             }
        }
    /**
     * 用户点击"确认记账" —— 将数据通过 sendJsonToJava 传给后端入库
     */
    static confirmRecord() {
        if (!this._currentFinanceData) return;

        const record = {
            category: this._currentFinanceData.category,
            unit_price: this._currentFinanceData.unit_price,
            label: this._currentFinanceData.label,
            income_expense: this._currentFinanceData.income_expense,
            quantity: this._currentFinanceData.quantity,
            total_price: this._currentFinanceData.total_price,
            timestamp: this._currentFinanceData.timestamp,
            raw_text:this._currentFinanceData.raw_text
        };

        this.sendJsonToJava(JSON.stringify(record));

        // 显示成功提示
        const chatBox = document.getElementById('chat-box');
        const successDiv = document.createElement('div');
        successDiv.className = 'message ai';
        successDiv.style.background = '#e6f7ff';
        successDiv.style.border = '1px solid #91d5ff';
        successDiv.innerText = '✅ 记账成功！';
        chatBox.appendChild(successDiv);
        chatBox.scrollTop = chatBox.scrollHeight;
    }

    /**
     * AI 请求失败
     */
    static failAiStreaming(error) {
        if (this._aiStreamDiv) {
            this._aiStreamDiv.innerText = '❌ ' + error;
            this._aiStreamDiv.classList.remove('streaming');
            this._aiStreamDiv = null;
        }
    }

}
