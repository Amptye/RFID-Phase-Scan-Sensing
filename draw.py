import os
from pathlib import Path
import re
import json
import matplotlib.pyplot as plt
import sys
import argparse

def bar_accuracy(file_path, output_dir="figures", output_file_name="accuracy_bar.png"):
    true_vals = []
    meas_vals = []

    pattern = re.compile(r"(\d+(?:\.\d+)?)\s*ml")

    try:
        with open(file_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("real test"):
                    continue
                matches = pattern.findall(line)
                if len(matches) >= 2:
                    true_vals.append(float(matches[0]))
                    meas_vals.append(float(matches[1]))
    except FileNotFoundError:
        print(f"错误：找不到文件 {file_path}")
    except Exception as e:
        print(f"处理文件{file_path}时发生错误：{e}")

    # 计算绝对误差
    errors = [abs(t - m) for t, m in zip(true_vals, meas_vals)]
    total = len(errors)

    # 统计比例
    acc_exact = sum(1 for e in errors if e == 0) / total * 100
    acc_5      = sum(1 for e in errors if e <= 5) / total * 100
    acc_10     = sum(1 for e in errors if e <= 10) / total * 100
    acc_15     = sum(1 for e in errors if e <= 15) / total * 100
    acc_ge25   = sum(1 for e in errors if e >= 25) / total * 100

    labels = [
        "Exact(==0)",
        "<=5ml",
        "<=10ml",
        "<=15ml",
        ">=25ml"
    ]
    values = [acc_exact, acc_5, acc_10, acc_15, acc_ge25]
    label_color_map = {
        "Exact(==0)" : "#53ee53",
        "<=5ml" : "#7ec8e3",
        "<=10ml" : "#3c9ee5",
        "<=15ml" : "#ffa500",
        ">=25ml" : "#f42727"
    }

    # 绘制柱状图
    plt.figure(figsize=(8, 5))
    bars = plt.bar(labels, values, color=[label_color_map[label] for label in labels], edgecolor="black")

    # 柱顶标百分比
    for bar, v in zip(bars, values):
        plt.text(
            bar.get_x() + bar.get_width() / 2,
            v + 0.5,
            f"{v:.2f}%",
            ha="center", # 水平对齐：文本的水平中心对准上面设置的横坐标
            va="bottom", # 竖直对齐：文本的底部对准上面设置的纵坐标
            fontsize=9
        )

    plt.ylabel("Proportion (%)")
    plt.title("Measurement Error Proportion")
    plt.ylim(0, max(values) * 1.15)
    plt.tight_layout()

    # 保存和显示
    # 获取文件名
    filename = os.path.basename(file_path)
    output_name = generate_image_path(output_file_name, output_dir, "", "_" + os.path.splitext(filename)[0])
    plt.savefig(output_name, dpi=300, bbox_inches='tight')
    print(f"\n图片已保存到: {output_name}")
    # plt.show()

def plot_multiple_json_files(folder_path, output_dir="figures", output_file_name="combined_plot.png"):
    """
    将一个文件夹下的所有JSON文件绘制到一张图上
    
    参数:
    folder_path: 包含JSON文件的文件夹路径
    output_dir: 输出图片目录
    output_file_name: 输出图片的名称
    """
    
    # 查找所有JSON文件
    json_files = []
    for file in os.listdir(folder_path):
        if file.lower().endswith('.json'):
            json_files.append(os.path.join(folder_path, file))
    
    if not json_files:
        print(f"警告：在 {folder_path} 中没有找到JSON文件")
        return
    
    print(f"找到 {len(json_files)} 个JSON文件:")
    for file in json_files:
        print(f"  - {os.path.basename(file)}")
    
    plt.figure(figsize=(12, 8))
    
    colors = plt.cm.tab10.colors  # 使用不同的颜色
    markers = ['o', 's', '^', 'v', '<', '>', 'p', '*', 'h', 'D']  # 不同的标记
    linestyles = ['-', '--', '-.', ':']  # 不同的线型
    
    legend_labels = []
    
    for idx, json_file in enumerate(json_files):
        try:
            with open(json_file, 'r') as f:
                data = json.load(f)
            
            # 过滤掉y值为 负无穷 或 NaN 的点
            filtered_data = [point for point in data if point['y'] != '-Infinity' and point['y'] != 'NaN']
            
            if not filtered_data:
                print(f"警告：{json_file} 中没有有效数据点")
                continue
            
            # 提取x和y坐标
            x_values = [point['x'] for point in filtered_data]
            y_values = [point['y'] for point in filtered_data]
            
            # 选择样式
            color = colors[idx % len(colors)]
            marker = markers[idx % len(markers)]
            linestyle = linestyles[idx % len(linestyles)]
            
            # 绘制曲线
            filename = os.path.basename(json_file)
            label = os.path.splitext(filename)[0]  # 去掉扩展名作为图例标签
            
            plt.plot(x_values, y_values, 
                    marker=marker, 
                    linestyle=linestyle, 
                    linewidth=2, 
                    markersize=5,
                    color=color,
                    label=label)
            
            legend_labels.append(label)
            
        except Exception as e:
            print(f"处理文件 {json_file} 时出错: {e}")
    
    if legend_labels:
        # 设置图表属性
        plt.xlabel('x', fontsize=12)
        plt.ylabel('y', fontsize=12)
        plt.title(f'Multiple JSON Files from {os.path.basename(folder_path)}', fontsize=14)
        plt.grid(True, alpha=0.3)
        plt.legend(loc='best', fontsize=10)
        plt.tight_layout()
        
        # 保存和显示
        output_name = generate_image_path(output_file_name, output_dir)
        plt.savefig(output_name, dpi=300, bbox_inches='tight')
        print(f"\n图片已保存到: {output_name}")
        # plt.show()
    else:
        print("错误：没有可绘制的有效数据")


def plot_data(file_path, output_dir="figures"):
    try:
        # 读取JSON文件
        with open(file_path, 'r') as f:
            data = json.load(f)
        
        # 过滤掉y值为 负无穷 或 NaN 的点
        filtered_data = [point for point in data if point['y'] != '-Infinity' and point['y'] != 'NaN']
        
        if not filtered_data:
            print("警告：过滤后没有有效数据点！")
            return
        
        # 提取x和y坐标
        x_values = [point['x'] for point in filtered_data]
        y_values = [point['y'] for point in filtered_data]
        
        # 绘制图像
        plt.figure(figsize=(10, 6))
        plt.plot(x_values, y_values, marker='o', linestyle='-', 
                linewidth=2, markersize=4, color='blue')
        plt.xlabel('x')
        plt.ylabel('y')
        plt.title(f'Data Points from {file_path}')
        plt.grid(True, alpha=0.3)
        plt.tight_layout()
        
        # 显示图片
        # plt.show()
        
        # 可选：保存图片
        output_name = generate_image_path(file_path, output_dir)
        plt.savefig(output_name, dpi=300, bbox_inches='tight')
            
    except FileNotFoundError:
        print(f"错误：找不到文件 {file_path}")
    except json.JSONDecodeError:
        print(f"错误：文件 {file_path} 不是有效的JSON格式")
    except Exception as e:
        print(f"发生错误：{e}")


def generate_image_path(file_path, output_dir="figures", prefix="", suffix=""):
    """
    将文件路径转换为图片保存路径
    
    参数:
    json_path: JSON文件路径，如 "abc/def.json"
    output_dir: 输出目录，默认为 "figures"
    
    返回:
    图片保存路径，如 "figures/def.png"
    """
    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)
    
    # 获取文件名并替换扩展名
    filename = os.path.basename(file_path)
    png_filename = prefix + os.path.splitext(filename)[0] + suffix + ".png"
    
    # 返回完整路径
    return os.path.join(output_dir, png_filename)


def isFile(path):
    p=Path(path)
    return p.is_file()
def isDir(path):
    p=Path(path)
    return p.is_dir()

# 方法1：通过命令行参数传递文件路径
if __name__ == "__main__":
    # if len(sys.argv) < 2:
    #     folder_path = input("请输入包含JSON文件的文件夹路径: ").strip()
    # else:
    #     folder_path = sys.argv[1]
    # # 检查路径是否存在
    # if not os.path.exists(folder_path):
    #     print(f"错误：路径 '{folder_path}' 不存在")
    #     sys.exit(1)
    # # 检查是否是文件夹
    # if not os.path.isdir(folder_path):
    #     print(f"错误：'{folder_path}' 不是文件夹")
    #     sys.exit(1)
    # # 指定输出路径
    # output_path = "figures/combined_plot_2.png"
    # # 绘制所有JSON文件
    # plot_multiple_json_files(folder_path, output_path)


    parser = argparse.ArgumentParser(description='绘制JSON数据点图像')
    parser.add_argument('-c', '--combine', action='store_true')
    parser.add_argument('file_path', nargs='?', default=".\\BPI_Reference\\tag-features\\0ml.json", help='JSON文件路径')
    parser.add_argument('output_dir', nargs='?', default=".\\figures\\tag-features", help='图像输出目录')
    parser.add_argument('-a', '--accuracy', action='store_true')
    
    args = parser.parse_args()
    if args.accuracy:
        if len(sys.argv) >= 3:
            if isFile(args.file_path):
                bar_accuracy(args.file_path, args.output_dir)
            else:
                print("accuracy时，TXT文件路径需要是文件！！！")
        else:
            # 如果没有参数，提示用户输入
            file_path = input("请输入TXT文件路径: ")
            if isFile(args.file_path):
                bar_accuracy(file_path)
            else:
                print("accuracy时，TXT文件路径需要是文件！！！")

    elif args.combine:
        if len(sys.argv) >= 3:
            if isDir(args.file_path):
                plot_multiple_json_files(args.file_path, args.output_dir)
            else:
                print("combine时，JSON文件路径需要是目录！！！")
        else:
            # 如果没有参数，提示用户输入
            folder_path = input("请输入包含JSON文件的文件夹路径: ").strip()
            if isDir(folder_path):
                plot_multiple_json_files(folder_path)
            else:
                print("combine时，JSON文件路径需要是目录！！！")
    else:
        if len(sys.argv) >= 2:
            if isFile(args.file_path):
                plot_data(args.file_path, args.output_dir)
            else:
                print("非combine时，JSON文件路径需要是文件！！！")
        else:
            # 如果没有参数，提示用户输入
            file_path = input("请输入JSON文件路径: ")
            if isFile(args.file_path):
                plot_data(file_path)
            else:
                print("非combine时，JSON文件路径需要是文件！！！")
