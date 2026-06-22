# PVZ2 编程使用指南

这份文档只说明“植物大战僵尸2脚本”的编程部分。目标是：

- 用户看了能知道怎么写 PVZ2 脚本。
- AI 看了能知道怎么根据用户需求生成或修改 PVZ2 脚本。
- 脚本尽量使用动作校准变量，不直接写死屏幕坐标。

## 1. 基本写法

PVZ2 脚本使用 Lua 风格代码。

最常用的结构是：

```lua
tap(坐标x, 坐标y)
wait(毫秒)

if 条件 then
    执行动作
end

while true do
    循环执行
    wait(500)
end
```

点位变量都有 `.x` 和 `.y`：

```lua
tap(plant_slots[1].x, plant_slots[1].y)
tap(board[1][1].x, board[1][1].y)
tap(other_start_battle.x, other_start_battle.y)
```

推荐写法是使用校准变量：

```lua
tap(plant_slots[1].x, plant_slots[1].y)
tap(board[3][5].x, board[3][5].y)
tap(sun_buy_key.x, sun_buy_key.y)
```

不推荐写死坐标：

```lua
tap(32.50, 76.20)
```

因为不同设备分辨率和游戏画面布局可能不同。

### 运行前权限准备

首次安装或重新安装后，需要在主界面完成以下授权：

- 无障碍服务：用于执行点击、滑动和种植等手势。
- 悬浮窗权限：用于显示 PVZ2 控制面板和编程编辑器。
- 屏幕捕获：用于颜色检测和 OCR 文字检测。

授权屏幕捕获时，如果系统支持“仅共享一个应用”，推荐只选择植物大战僵尸2。这样
文字和颜色检测只会读取游戏画面，不会把本应用悬浮窗包含进截图。屏幕捕获被关闭或
系统回收后，需要回到主界面重新授权。

## 2. 坐标规则

脚本中的数字坐标使用百分比坐标，范围是 `0 ~ 100`，可以使用小数。

```lua
tap(50, 50)
tap(15.35, 46.94)
```

`50, 50` 表示屏幕正中央。

校准变量也可以直接用于脚本函数：

```lua
tap(plant_slots[1].x, plant_slots[1].y)
check_color("#A020F0", 10, cards_edge.x, cards_edge.y)
```

注意：

- 不要把自己设备上的像素坐标直接写进脚本。
- 文字检测区域的 `左, 上, 右, 下` 也是百分比坐标。
- 颜色检测点位的 `x, y` 也是百分比坐标，或使用校准变量的 `.x, .y`。

## 3. 插入工具

PVZ2 编程编辑器里的“插入工具”有 4 项。

### 点击

点击“点击”会插入：

```lua
tap()
```

然后把模板中的坐标填进去，例如：

```lua
tap(plant_slots[1].x, plant_slots[1].y)
```

### 滑动

点击“滑动”会插入：

```lua
swipe(, , , )
```

需要填入起点和终点，建议再补上持续时间：

```lua
swipe(起点x, 起点y, 终点x, 终点y, 持续毫秒)
```

示例：

```lua
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y, 50)
```

### 文字识别

点击“文字识别”会进入框选界面。

保存范围后会自动插入类似代码：

```lua
check_text("文字", 左, 上, 右, 下)
```

这个工具只负责插入识别范围，不会自动把当前画面的文字替换进代码。需要手动把
`"文字"` 改成要识别的内容：

```lua
if check_text("继续", 10.00, 20.00, 40.00, 30.00) then
    tap(other_continue.x, other_continue.y)
end
```

文字检测只使用 `check_text(...)` 这一种方法，不要再给 PVZ2 脚本生成其他文字检测写法。

当前版本使用离线 PaddleOCR，并且始终识别原始游戏截图，没有灰度、黑白或反色滤镜。
每次调用 `check_text(...)` 都会截取当时的游戏画面，因此可以检测动态变化的文字。
第一次文字检测可能需要初始化 OCR；后续检测通常更快。

### 颜色识别

点击“颜色识别”会进入取色界面。

确认颜色后会插入：

```lua
check_color("#颜色", 10, )
```

最后一个逗号后面需要补坐标，例如用模板填入点位：

```lua
check_color("#A020F0", 10, cards_edge.x, cards_edge.y)
```

可配合 `if` 使用：

```lua
if check_color("#A020F0", 10, cards_edge.x, cards_edge.y) then
    tap(cards_edge.x, cards_edge.y)
end
```

## 4. 模板用法

PVZ2 模板只插入坐标内容，不会自动加 `tap()`，也不会自动换行。

例如光标在这里：

```lua
tap()
```

把光标放到括号中：

```lua
tap(|)
```

点击“植物卡槽”模板后会变成：

```lua
tap(plant_slots[].x, plant_slots[].y)
```

然后把 `[]` 改成具体编号：

```lua
tap(plant_slots[1].x, plant_slots[1].y)
```

当前模板内容：

```lua
植物卡槽      plant_slots[].x, plant_slots[].y
种植棋盘      board[][].x, board[][].y
阳光相关      sun_.x, sun_.y
能量豆相关    plant_food_.x, plant_food_.y
神器相关      artifact_.x, artifact_.y
黄瓜相关      cucumber_.x, cucumber_.y
充值相关      recharge_.x, recharge_.y
扑克牌        cards_edge.x, cards_edge.y
其他位置      other_.x, other_.y
无尽补给相关  endless_supply_.x, endless_supply_.y
```

模板里的空位需要手动补全。

示例：

```lua
sun_.x, sun_.y
```

改成：

```lua
sun_buy_key.x, sun_buy_key.y
```

无尽补给相关模板会插入：

```lua
endless_supply_.x, endless_supply_.y
```

改成：

```lua
endless_supply_ability.x, endless_supply_ability.y
```

如果要使用无尽补给的识别框，不走这个点位模板，直接写：

```lua
endless_supply_text_area.left,
endless_supply_text_area.top,
endless_supply_text_area.right,
endless_supply_text_area.bottom
```

## 5. 常用函数

### tap

点击一个位置。

```lua
tap(x, y)
tap(x, y, 按压毫秒)
```

第三个参数是按压持续时间，单位是毫秒，不是点击次数。

示例：

```lua
tap(other_start_battle.x, other_start_battle.y)
tap(artifact_main.x, artifact_main.y, 50)
```

### swipe

从一个位置滑到另一个位置。

```lua
swipe(起点x, 起点y, 终点x, 终点y)
swipe(起点x, 起点y, 终点x, 终点y, 持续毫秒)
```

第五个参数是滑动持续时间，单位是毫秒。项目里不写时默认约 `300` 毫秒，但游戏脚本建议明确写出来，常用 `30 ~ 100`。

示例：

```lua
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y, 50)
```

### wait

等待指定毫秒。

```lua
wait(500)
wait(1000)
```

强制要求：任何 `while true` 或其他长期循环里，必须包含至少一条 `wait(...)`。推荐最小值是：

```lua
wait(10)
```

没有等待的循环会占用过高 CPU，脚本可能被系统停止。

### check_text

检查某个区域内是否出现文字。

```lua
check_text("文字", 左, 上, 右, 下)
```

`左, 上, 右, 下` 是百分比坐标，范围 `0 ~ 100`。

OCR 比颜色检测耗时。循环检测文字时，建议在每轮末尾使用 `wait(200)` 到
`wait(500)`，并尽量缩小识别范围。

示例：

```lua
if check_text("继续", 10.00, 20.00, 40.00, 30.00) then
    tap(other_continue.x, other_continue.y)
end
```

### check_text_not

检查某个区域内是否没有出现文字。

```lua
if check_text_not("暂停", 10.00, 20.00, 40.00, 30.00) then
    tap(other_pause.x, other_pause.y)
end
```

### check_color

检查某个位置颜色是否匹配。

```lua
check_color("#颜色", 容差, x, y)
```

容差一般使用 `10`。`x, y` 是百分比坐标，也可以使用校准变量。

示例：

```lua
if check_color("#A020F0", 10, cards_edge.x, cards_edge.y) then
    tap(cards_edge.x, cards_edge.y)
end
```

### check_color_not

检查某个位置颜色是否不匹配。

```lua
if check_color_not("#A020F0", 10, cards_edge.x, cards_edge.y) then
    wait(500)
end
```

### parallel

并行执行多个函数。

```lua
parallel(
    function()
        tap(plant_slots[1].x, plant_slots[1].y)
    end,
    function()
        tap(board[1][1].x, board[1][1].y)
    end
)
```

常见错误：

```lua
parallel(function() tap(plant_slots[1].x, plant_slots[1].y) end function() tap(board[1][1].x, board[1][1].y) end)
```

上面是错误写法，因为两个 `function() ... end` 中间缺少逗号。

正确写法：

```lua
parallel(
    function()
        tap(plant_slots[1].x, plant_slots[1].y)
    end,
    function()
        tap(board[1][1].x, board[1][1].y)
    end
)
```

每一路 `function() ... end` 之间必须用逗号分隔，最后一路后面不用再加逗号。

## 6. 动作持续时间建议

| 动作类型 | 推荐持续时间 | 备注 |
| --- | --- | --- |
| 普通点击 | 不写，或 `1 ~ 10` 毫秒 | 大部分界面点击不需要长按 |
| 长按点击 | `10 ~ 50` 毫秒 | 部分按钮或神器可能需要更稳定触发 |
| 滑动种植 | `30 ~ 100` 毫秒 | 太快可能无效，太慢会影响节奏 |
| 滑动给豆 | `30 ~ 100` 毫秒 | 从能量豆滑到植物或棋盘 |
| 滑动铲除 | `30 ~ 100` 毫秒 | 从铲子滑到植物，或先点铲子再点格子 |

## 7. 同时执行两个动作

如果需要两个动作同时执行，使用 `parallel(...)`。

最常见写法：

```lua
parallel(
    function()
        动作1
    end,
    function()
        动作2
    end
)
```

示例：同时点击第 1 个植物卡槽和第 1 行第 1 列棋盘：

```lua
parallel(
    function()
        tap(plant_slots[1].x, plant_slots[1].y)
    end,
    function()
        tap(board[1][1].x, board[1][1].y)
    end
)
```

示例：同时点击两个不同位置：

```lua
parallel(
    function()
        tap(other_speed_up.x, other_speed_up.y)
    end,
    function()
        tap(other_next_wave.x, other_next_wave.y)
    end
)
```

如果要同时执行更多动作，可以继续添加 `function() ... end`：

```lua
parallel(
    function()
        tap(plant_slots[1].x, plant_slots[1].y)
    end,
    function()
        tap(board[1][1].x, board[1][1].y)
    end,
    function()
        wait(300)
        tap(other_next_wave.x, other_next_wave.y)
    end
)
```

注意：

- `parallel` 里面每个 `function() ... end` 是一路同时执行的动作。
- 如果两个动作之间必须有先后顺序，不要放进同一个 `parallel`。
- 并行动作里也可以写 `wait(...)`，用于控制这一条动作自己的节奏。

## 8. 常见脚本示例

这一节按 `E:\test\value` 里的 7 个 PVZ2 脚本整理，示例结构和你当前脚本保持一致。

### 等扑克牌开始

你的脚本通常先等扑克牌边缘的紫色出现，再开始执行后面的动作。

```lua
while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

wait(100)
```

### 开启加速

```lua
tap(other_speed_up.x, other_speed_up.y, 1)
wait(100)
```

### 第一关写法

第一关主要是加速、快速种多个植物，然后并行点下一波并检测最后一波红字。

```lua
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

tap(other_speed_up.x, other_speed_up.y, 1)

wait(30)
swipe(plant_slots[8].x, plant_slots[8].y, board[2][4].x, board[2][4].y, 30)
wait(30)
swipe(plant_slots[8].x, plant_slots[8].y, board[4][5].x, board[4][5].y, 30)

wait(100)
swipe(plant_slots[5].x, plant_slots[5].y, board[3][5].x, board[3][5].y, 10)

wait(100)
swipe(plant_slots[7].x, plant_slots[7].y, board[3][4].x, board[3][4].y, 10)

wait(100)
swipe(plant_slots[4].x, plant_slots[4].y, board[3][3].x, board[3][3].y, 10)

wait(100)
swipe(plant_slots[3].x, plant_slots[3].y, board[3][3].x, board[3][3].y, 10)

wait(100)
swipe(plant_slots[6].x, plant_slots[6].y, board[3][3].x, board[3][3].y, 10)
wait(100)
swipe(plant_slots[6].x, plant_slots[6].y, board[3][5].x, board[3][5].y, 10)

wait(30)
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y, 30)
wait(30)
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y, 30)
```

### 第二关写法

第二关主要是先给豆几个关键位置，再种能量花。

```lua
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

tap(other_speed_up.x, other_speed_up.y, 1)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[2][4].x, board[2][4].y, 100)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[4][5].x, board[4][5].y, 100)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][4].x, board[3][4].y, 100)

wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y, 100)

wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y, 100)
```

### 第三关写法

第三关包含给豆、融合电大和种能量花。

```lua
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

wait(10)
tap(other_speed_up.x, other_speed_up.y, 1)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][3].x, board[3][3].y, 100)

wait(100)
swipe(plant_slots[2].x, plant_slots[2].y, board[2][4].x, board[2][4].y, 100)
wait(100)
swipe(plant_slots[2].x, plant_slots[2].y, board[4][5].x, board[4][5].y, 100)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y, 100)

wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y, 100)
wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y, 100)
```

### 前期脚本写法

前期脚本常见流程是给豆、种能量花、再使用神器大招。

```lua
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

tap(other_speed_up.x, other_speed_up.y, 1)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][3].x, board[3][3].y, 100)

wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y, 100)

wait(100)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y, 100)

wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y, 100)

wait(60)
tap(artifact_main.x, artifact_main.y, 1)
tap(artifact_large.x, artifact_large.y, 1)
```

### 后期葫芦写法

后期葫芦脚本会先布置关键植物，再点击神器并等待后续大招。

```lua
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

tap(other_speed_up.x, other_speed_up.y, 1)

wait(50)
swipe(plant_food_bean.x, plant_food_bean.y, board[2][2].x, board[2][2].y, 10)

wait(50)
swipe(plant_slots[2].x, plant_slots[2].y, board[3][9].x, board[3][9].y, 10)

wait(50)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][9].x, board[3][9].y, 10)

wait(50)
swipe(plant_slots[1].x, plant_slots[1].y, board[5][2].x, board[5][2].y, 10)

wait(50)
swipe(plant_slots[1].x, plant_slots[1].y, board[5][3].x, board[5][3].y, 10)

wait(100)
tap(artifact_main.x, artifact_main.y, 10)
wait(1400)
tap(artifact_large.x, artifact_large.y, 10)
```

### 后期保龄球写法

后期保龄球脚本前半段和葫芦类似，但神器只点击一次，然后等待。

```lua
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

tap(other_speed_up.x, other_speed_up.y, 1)

wait(50)
swipe(plant_food_bean.x, plant_food_bean.y, board[2][2].x, board[2][2].y, 10)

wait(50)
swipe(plant_slots[2].x, plant_slots[2].y, board[3][9].x, board[3][9].y, 10)

wait(50)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][9].x, board[3][9].y, 10)

wait(50)
swipe(plant_slots[1].x, plant_slots[1].y, board[5][2].x, board[5][2].y, 10)

wait(50)
swipe(plant_slots[1].x, plant_slots[1].y, board[5][3].x, board[5][3].y, 10)

wait(300)
tap(artifact_main.x, artifact_main.y, 10)
wait(1100)
```

### boss 脚本写法

boss 脚本先给豆两个关键格子，再加速并补种能量花。

```lua
while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
    wait(1)
end

wait(100)

swipe(plant_food_bean.x, plant_food_bean.y, board[4][5].x, board[4][5].y, 30)
wait(100)

swipe(plant_food_bean.x, plant_food_bean.y, board[4][4].x, board[4][4].y, 30)
wait(100)

tap(other_speed_up.x, other_speed_up.y, 1)
wait(100)

swipe(plant_slots[1].x, plant_slots[1].y, board[5][2].x, board[5][2].y, 30)
wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[5][3].x, board[5][3].y, 30)
```

### 自动点下一波并检测结束

你的多个脚本最后都会用这个结构：一路持续点击“下一波”，另一路检测“最后一波字幕红色”，检测到后把 `flag` 改成 `0` 停止循环。

```lua
parallel(
    function()
        while flag == 1 do
            wait(1)
            tap(other_next_wave.x, other_next_wave.y, 1)
        end
    end,
    function()
        while flag == 1 do
            if check_color("#FF0000", 10, other_final_wave_red.x, other_final_wave_red.y) then
                flag = 0
            end

            wait(1)
        end
    end
)
```

后期脚本还可以在检测到最后一波红字后补一个给豆动作：

```lua
parallel(
    function()
        while flag == 1 do
            wait(1)
            tap(other_next_wave.x, other_next_wave.y, 1)
        end
    end,
    function()
        while flag == 1 do
            if check_color("#FF0000", 10, other_final_wave_red.x, other_final_wave_red.y) then
                flag = 0
                wait(60)
                swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y, 10)
            end

            wait(1)
        end
    end
)
```

## 9. 校准变量速查

### 植物卡槽

```lua
plant_slot_1
plant_slot_2
plant_slot_3
plant_slot_4
plant_slot_5
plant_slot_6
plant_slot_7
plant_slot_8
plant_slots
```

推荐：

```lua
tap(plant_slots[1].x, plant_slots[1].y)
tap(plant_slots[8].x, plant_slots[8].y)
```

### 种植棋盘

棋盘是 5 行 9 列。

```lua
board_r1_c1 到 board_r5_c9
board
```

推荐：

```lua
tap(board[1][1].x, board[1][1].y)
tap(board[5][9].x, board[5][9].y)
```

也可以：

```lua
tap(board_r1_c1.x, board_r1_c1.y)
tap(board_r5_c9.x, board_r5_c9.y)
```

### 阳光相关

```lua
sun_buy_key       购买阳关键
sun_ad            广告
sun_10_diamond    10钻石
sun_close         关闭
sun_points
```

示例：

```lua
tap(sun_buy_key.x, sun_buy_key.y)
tap(sun_points.sun_buy_key.x, sun_points.sun_buy_key.y)
```

### 能量豆相关

```lua
plant_food_bean   豆，给豆操作的起点
plant_food_plus   +
plant_food_buy    购买
plant_food_points
```

示例：

```lua
tap(plant_food_bean.x, plant_food_bean.y)
tap(plant_food_points.plant_food_bean.x, plant_food_points.plant_food_bean.y)
swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y, 50)
```

### 神器相关

```lua
artifact_main     神器
artifact_small    小
artifact_medium   中
artifact_large    大
artifact_points
```

示例：

```lua
tap(artifact_main.x, artifact_main.y)
tap(artifact_points.artifact_main.x, artifact_points.artifact_main.y)
```

### 黄瓜相关

```lua
cucumber_main     黄瓜
cucumber_drop     下瓜
cucumber_close    关闭
cucumber_points
```

示例：

```lua
tap(cucumber_main.x, cucumber_main.y)
tap(cucumber_points.cucumber_main.x, cucumber_points.cucumber_main.y)
```

### 充值相关

```lua
recharge_main     充值
recharge_close    关闭
recharge_points
```

示例：

```lua
tap(recharge_main.x, recharge_main.y)
tap(recharge_points.recharge_main.x, recharge_points.recharge_main.y)
```

### 扑克牌

```lua
cards_edge        将中心点放到扑克牌边缘的紫色地方
cards_points
```

示例：

```lua
tap(cards_edge.x, cards_edge.y)
tap(cards_points.cards_edge.x, cards_points.cards_edge.y)
check_color("#A020F0", 10, cards_edge.x, cards_edge.y)
```

### 其他位置

其他位置共 11 个点。新增或修改这里的点位后，需要重新进入“其他位置”校准并保存一次。

```lua
other_speed_up             加速
other_pause                暂停
other_continue             继续
other_restart              重新开始
other_back_to_map          返回地图
other_shovel               铲子
other_card_start_battle    选卡的开始战斗
other_start_battle         开始战斗
other_final_wave_red       最后一波字幕（红色）
other_next_wave            下一波
other_switch_form          切换形态
other_points
```

示例：

```lua
tap(other_speed_up.x, other_speed_up.y)
tap(other_points.other_speed_up.x, other_points.other_speed_up.y)
tap(other_next_wave.x, other_next_wave.y)
tap(other_shovel.x, other_shovel.y)
tap(other_switch_form.x, other_switch_form.y)
```

### 无尽补给相关

无尽补给相关包含 1 个文字识别框和 9 个点击圆圈。校准时需要把“识别框”移动/缩放到要识别文字的位置，并把 9 个圆圈分别放到对应按钮上。

校准交互：

- 点击“识别框”后会选中识别框，识别框出现黄色边框。
- 识别框选中后，可以在屏幕其他空白位置拖动，识别框会跟着移动。
- 拖动识别框四个角，可以调整识别框大小。
- 点击任意圆圈后会选中该圆圈，圆圈出现黄色边框。
- 圆圈选中后，可以在屏幕其他空白位置拖动，选中的圆圈会跟着移动。
- 点击其他框或圆圈会切换当前选中对象。

```lua
endless_supply_text_area
endless_supply_ability
endless_supply_blue_confirm
endless_supply_green_confirm
endless_supply_final_confirm
endless_supply_pair
endless_supply_1
endless_supply_2
endless_supply_3
endless_supply_continue_challenge
endless_supply_points
```

识别框字段：

```lua
endless_supply_text_area.left
endless_supply_text_area.top
endless_supply_text_area.right
endless_supply_text_area.bottom
```

点击点位：

```lua
endless_supply_ability             能力
endless_supply_blue_confirm        蓝色确定
endless_supply_green_confirm       绿色确定
endless_supply_final_confirm       最后确定
endless_supply_pair                配对
endless_supply_1                   1
endless_supply_2                   2
endless_supply_3                   3
endless_supply_continue_challenge  继续挑战
```

示例：

```lua
if check_text(
    "补给",
    endless_supply_text_area.left,
    endless_supply_text_area.top,
    endless_supply_text_area.right,
    endless_supply_text_area.bottom
) then
    tap(endless_supply_ability.x, endless_supply_ability.y)
    wait(100)
    tap(endless_supply_blue_confirm.x, endless_supply_blue_confirm.y)
end
```

新增点位示例：

```lua
tap(endless_supply_pair.x, endless_supply_pair.y)
tap(endless_supply_1.x, endless_supply_1.y)
tap(endless_supply_2.x, endless_supply_2.y)
tap(endless_supply_3.x, endless_supply_3.y)
tap(endless_supply_continue_challenge.x, endless_supply_continue_challenge.y)
```

也可以通过表访问：

```lua
tap(endless_supply_points.endless_supply_ability.x, endless_supply_points.endless_supply_ability.y)
```

## 10. 给 AI 的使用规则

AI 帮用户写 PVZ2 脚本时，按这些规则来：

1. 优先使用校准变量，不要直接写死屏幕坐标。
2. 用户说“第几张植物卡”时，使用 `plant_slots[n]`。
3. 用户说“第几行第几列”时，使用 `board[行][列]`。
4. 用户说“开始战斗”，优先使用 `other_start_battle`。
5. 用户说“选卡的开始战斗”，使用 `other_card_start_battle`。
6. 用户说“继续、暂停、加速、重新开始、返回地图、铲子、下一波、切换形态”，使用 `other_` 对应变量。
7. 用户说“无尽补给相关”时，优先使用 `endless_supply_text_area` 做文字识别区域，使用 `endless_supply_ability`、`endless_supply_blue_confirm`、`endless_supply_green_confirm`、`endless_supply_final_confirm`、`endless_supply_pair`、`endless_supply_1`、`endless_supply_2`、`endless_supply_3`、`endless_supply_continue_challenge` 做点击点。
8. 用户要识别文字时，只生成 `if check_text(...) then ... end`，并提醒文字范围需要用插入工具框选；如果是无尽补给文字，优先使用已校准的 `endless_supply_text_area`。
9. 用户要识别颜色时，生成 `if check_color("#颜色", 10, x, y) then ... end`，颜色和坐标可以让用户用插入工具与模板补全。
10. 用户没有说明循环时，不要默认写无限循环；需要重复检测时再写 `while true do ... wait(...) end`。
11. 任何循环里必须加 `wait(...)`，最低建议 `wait(10)`。
12. 对点击动作之间加短等待，例如 `wait(100)` 或 `wait(300)`。
13. `tap` 第三个参数是按压毫秒，不是点击次数。
14. `swipe` 建议写第五个参数，游戏动作常用 `30 ~ 100` 毫秒。
15. 生成 `parallel(...)` 时，每个 `function() ... end` 之间必须用逗号分隔。
16. 生成脚本时保持 Lua 语法完整，不要留下未补全的占位符，除非用户明确要求保留空位。
17. 文字识别始终使用原始游戏截图，不要建议用户选择 OCR 滤镜。
18. 使用文字或颜色检测前，提醒用户先授权屏幕捕获；系统支持时推荐只共享 PVZ2。

## 11. AI 生成脚本的常用模板

### 单次动作

```lua
tap(目标.x, 目标.y)
wait(300)
```

### 选植物并种植

```lua
tap(plant_slots[卡槽编号].x, plant_slots[卡槽编号].y)
wait(100)
tap(board[行][列].x, board[行][列].y)
wait(300)
```

### 滑动种植

```lua
swipe(plant_slots[卡槽编号].x, plant_slots[卡槽编号].y, board[行][列].x, board[行][列].y, 50)
wait(300)
```

### 给豆

```lua
swipe(plant_food_bean.x, plant_food_bean.y, board[行][列].x, board[行][列].y, 50)
wait(300)
```

### 文字出现后点击

```lua
if check_text("文字", 左, 上, 右, 下) then
    tap(目标.x, 目标.y)
end
```

### 颜色匹配后点击

```lua
if check_color("#颜色", 10, 目标.x, 目标.y) then
    tap(目标.x, 目标.y)
end
```

### 循环检测

```lua
while true do
    if check_text("文字", 左, 上, 右, 下) then
        tap(目标.x, 目标.y)
    end

    wait(500)
end
```
