# -*- coding: utf-8 -*-
"""
Delta phi at different frequencies
横轴: Transmit Power (dBm)
纵轴: Delta phi (rad)
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib import rcParams

# ---- 字体配置 ----
rcParams['font.family'] = 'sans-serif'
rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Helvetica']
rcParams['mathtext.fontset'] = 'stix'
rcParams['axes.unicode_minus'] = False

# ---- 数据 ----
# 横轴: Transmit Power (dBm)
ptx = [12.00, 12.25, 12.50, 12.75, 13.00, 13.25,
       13.50, 13.75, 14.00, 14.25, 14.50, 14.75]

# 每个频点对应的 Delta phi 值; None 表示该点缺失
data = {
    902.75: [None, None, 0.2613, 0.2620, 0.2693, 0.2785,
             0.2771, 0.2953, 0.2807, 0.2822, 0.2812, 0.2816],
    903.25: [None, None, 0.2480, 0.2565, 0.2620, 0.2667,
             0.2691, 0.2698, 0.2786, 0.2665, 0.2688, 0.2612],
    903.75: [None, None, 0.2378, 0.2452, 0.2445, 0.2540,
             0.2579, 0.2520, 0.2513, 0.2677, 0.2615, 0.2653],
    904.25: [None, None, 0.2455, 0.2575, 0.2603, 0.2752,
             0.2695, 0.2718, 0.2686, 0.2744, 0.2745, 0.2791],
    905.25: [None, None, 0.2281, 0.2284, 0.2405, 0.2426,
             0.2452, 0.2434, 0.2370, 0.2518, 0.2487, 0.2482],
    906.25: [None, None, 0.2197, 0.2233, 0.2303, 0.2387,
             0.2406, 0.2465, 0.2460, 0.2570, 0.2620, 0.2644],
    907.25: [None, None, 0.2387, 0.2453, 0.2560, 0.2510,
             0.2546, 0.2582, 0.2260, 0.2603, 0.2523, 0.2644],
}

# 移除 904.75 频点 Ptx=12.00 的数据点(该点在原表中本身即为缺失, 此处为示例保留接口)
# 如需移除某个点, 将数据改为 None 即可

# ---- 绘图 ----
fig, ax = plt.subplots(figsize=(11, 6.5), dpi=150)

colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728',
          '#9467bd', '#8c564b', '#e377c2']
markers = ['o', 's', '^', 'D', 'v', 'P', '*']

for (freq, values), c, m in zip(data.items(), colors, markers):
    x_plot, y_plot = [], []
    for x, y in zip(ptx, values):
        if y is not None:
            x_plot.append(x)
            y_plot.append(y)
    label = f'{freq:g} MHz'  # 频点标签; 如需 GHz 自行换算
    ax.plot(x_plot, y_plot, marker=m, markersize=5, linewidth=1.6,
            color=c, label=label, zorder=3)

# ax.set_title(r'$\Delta\phi$ at different frequencies', fontsize=15, pad=12)
ax.set_xlabel('Transmit Power (dBm)', fontsize=13)
ax.set_ylabel(r'$\Delta\phi$ (rad)', fontsize=13)

ax.set_xlim(11.9, 14.85)
ax.set_ylim(0.21, 0.31)
ax.set_xticks(ptx)
ax.tick_params(axis='both', labelsize=11)

ax.grid(True, linestyle='--', alpha=0.4, zorder=0)
ax.legend(title='Frequencies', fontsize=10, title_fontsize=11,
          loc='upper left', frameon=True, framealpha=0.9)

for spine in ['top', 'right']:
    ax.spines[spine].set_visible(False)

plt.tight_layout()
plt.savefig('../Phase_Ptag_ITP_2.pdf', dpi=150, bbox_inches='tight')
print('ok')