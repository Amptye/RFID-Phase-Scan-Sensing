def gen_line_points(x_start, x_end, y_start, k, x_step):
    points = []
    if x_end < x_start:
        x_start, x_end = x_end, x_start
        y_start = y_start - k * (x_end - x_start)
    if x_step < 0:
        xstep = -xstep
    x = x_start
    while x <= x_end + 1e-9:  # 浮点容差
        y = y_start + k * (x - x_start)
        points.append(
            '{"x":%.4g,"y":%.4g}' % (round(x, 10), round(y, 10))
        )
        x += x_step

    return ",".join(points)

x_start = 10.5
x_end = 0
y_start = -47.5
k = -3.0
step = 0.25

print(gen_line_points(x_start, x_end, y_start, k, step))