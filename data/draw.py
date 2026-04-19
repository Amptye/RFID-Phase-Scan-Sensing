import os
import json
import matplotlib.pyplot as plt
import sys
import argparse

def plot_multiple_json_files(folder_path, output_path="combined_plot.png"):
    """
    将一个文件夹下的所有JSON文件绘制到一张图上
    
    参数:
    folder_path: 包含JSON文件的文件夹路径
    output_path: 输出图片路径
    """
    # 确保输出目录存在
    output_dir = os.path.dirname(output_path)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
    
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
            
            # 过滤掉y值为负无穷的点
            filtered_data = [point for point in data if point['y'] != '-Infinity']
            
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
        plt.savefig(output_path, dpi=300, bbox_inches='tight')
        print(f"\n图片已保存到: {output_path}")
        # plt.show()
    else:
        print("错误：没有可绘制的有效数据")


def plot_data(file_path):
    try:
        # 读取JSON文件
        with open(file_path, 'r') as f:
            data = json.load(f)
        
        # 过滤掉y值为负无穷的点
        filtered_data = [point for point in data if point['y'] != '-Infinity']
        
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
        output_name = generate_image_path(file_path)
        plt.savefig(output_name, dpi=300, bbox_inches='tight')
            
    except FileNotFoundError:
        print(f"错误：找不到文件 {file_path}")
    except json.JSONDecodeError:
        print(f"错误：文件 {file_path} 不是有效的JSON格式")
    except Exception as e:
        print(f"发生错误：{e}")


def generate_image_path(json_path, output_dir="figures"):
    """
    将JSON文件路径转换为图片保存路径
    
    参数:
    json_path: JSON文件路径，如 "abc/def.json"
    output_dir: 输出目录，默认为 "figures"
    
    返回:
    图片保存路径，如 "figures/def.png"
    """
    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)
    
    # 获取文件名并替换扩展名
    filename = os.path.basename(json_path)
    png_filename = os.path.splitext(filename)[0] + ".png"
    
    # 返回完整路径
    return os.path.join(output_dir, png_filename)


# 方法1：通过命令行参数传递文件路径
if __name__ == "__main__":
    if len(sys.argv) < 2:
        folder_path = input("请输入包含JSON文件的文件夹路径: ").strip()
    else:
        folder_path = sys.argv[1]
    # 检查路径是否存在
    if not os.path.exists(folder_path):
        print(f"错误：路径 '{folder_path}' 不存在")
        sys.exit(1)
    # 检查是否是文件夹
    if not os.path.isdir(folder_path):
        print(f"错误：'{folder_path}' 不是文件夹")
        sys.exit(1)
    # 指定输出路径
    output_path = "figures/combined_plot.png"
    # 绘制所有JSON文件
    plot_multiple_json_files(folder_path, output_path)


    # parser = argparse.ArgumentParser(description='绘制JSON数据点图像')
    # parser.add_argument('filepath', help='JSON文件路径')
    
    # if len(sys.argv) > 1:
    #     args = parser.parse_args()
    #     plot_data(args.filepath)
    # else:
    #     # 方法2：如果没有参数，提示用户输入
    #     file_path = input("请输入JSON文件路径: ")
    #     plot_data(file_path)