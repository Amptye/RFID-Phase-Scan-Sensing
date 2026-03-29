const dataContainer = document.getElementById('data-container');
const timestampElement = document.getElementById('timestamp');

// 连接到WebSocket服务器
const socket = new WebSocket(`ws://localhost:8080/data`);

socket.onopen = function(e) {
    console.log("WebSocket连接已建立");
};

socket.onmessage = function(event) {
    const data = JSON.parse(event.data);
    updateUI(data);
};

socket.onclose = function(event) {
    if (event.wasClean) {
        console.log(`连接关闭，代码=${event.code} 原因=${event.reason}`);
    } else {
        console.log('连接中断');
    }
};

socket.onerror = function(error) {
    console.log(`WebSocket错误: ${error.message}`);
};

// 更新UI的函数
function updateUI(data) {
    let html = '';
    for (const [label, value] of Object.entries(data)) {

        const autoTuneData = value.autoTuneData;
        const rssi = value.rssi;
        const bpi = value.bpi;

        const disAutoTune = autoTuneData.disAutoTune;
        const valueAutoTune = autoTuneData.valueAutoTune;

        // 根据值决定显示内容和样式
        const autoTuneValue = disAutoTune ? "AutoTune 功能被禁用" : `${valueAutoTune}${"----".repeat(valueAutoTune)}`;
        const valueClass = disAutoTune ? "red-value" : "data-value";
        //const value_style = disAutoTune ? "" : `color: ${getHslColor(valueAutoTune, 0, 5)}`;
        // 得用反引号“`”才能用“${...}”
        rssi_min = -75;
        rssi_max = -30;
        const rssi_percentage = rssi < rssi_min ? 0 :
                                (rssi > rssi_max ? 100 :
                                (rssi-rssi_min)/(rssi_max-rssi_min)*100);

//        html += `
//            <div class="data-item">
//                <span class="data-label">${label}</span>
//                <span class="${valueClass}">${autoTuneValue}</span>
//            </div>
//        `;
        html += `
            <tr>
                <td class="left-align-td">
                    <span class="data-label">${label}</span>
                </td>
                <td>
                    <span class="${valueClass}">${autoTuneValue}</span>
                </td>
                <td>
                    <div class="rssi-item">
                        <span class="data-value">${rssi}</span>
                        <div class="rssi-bar">
                            <div class="rssi-fill" style="
                                width: ${rssi_percentage}%;
                                background-color: ${rssi_percentage > 60 ? '#28a745' :
                                                (rssi_percentage > 30 ? '#ffc107' : '#dc3545')};"
                                >
                            </div>
                        </div>
                    </div>
                </td>
                <td>
                    <span class="data-value">${bpi}</span>
                </td>
            </tr>
        `;
    }
    dataContainer.innerHTML = html;
    timestampElement.textContent = `最后更新时间: ${new Date().toLocaleTimeString()}`;
}

// 基于数值范围的HSL颜色映射
function getHslColor(value, min = 0, max = 100) {
  // 将值映射到HSL颜色模型的色相（0-360度）
  // HSL(色相, 饱和度%, 亮度%)
  // 0=红色=360，60=黄色，120=绿色，180=青色，240=蓝色，300=紫色，360=红色（回到起点）
  //const hue = (100 + ((value - min) / (max - min)) * 280) % 360; // 100~380，避开黄色，在白色背景下不显眼
  const hue = ((value - min) / (max - min)) * 360;
  return `hsl(${hue}, 100%, 50%)`; // 100%饱和度；亮度为50%时颜色最鲜艳（纯色）
}
