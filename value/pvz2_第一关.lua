-- AndroidClicker Lua Script
-- name: 第一关
-- loopCount: 1
-- loopGapMs: 0

wait(1)
flag=1

-- 等待扑克牌边缘紫色出现，确认关卡进入可操作阶段
while not 
check_color("#DF58FC", 10, cards_edge.x, cards_edge.y)
do
end

-- 开启游戏加速
tap(other_speed_up.x, other_speed_up.y, 1)

-- 8号植物分别种到第二行第四列、第四行第五列
wait(30)
swipe(plant_slots[8].x, plant_slots[8].y, board[2][4].x, board[2][4].y,30)
wait(30)
swipe(plant_slots[8].x, plant_slots[8].y, board[4][5].x, board[4][5].y,30)

-- 5号植物种到第三行第五列
wait(100)
swipe(plant_slots[5].x, plant_slots[5].y, board[3][5].x, board[3][5].y,10)

-- 7号植物种到第三行第四列
wait(100)
swipe(plant_slots[7].x, plant_slots[7].y, board[3][4].x, board[3][4].y,10)

-- 连续点击两次切换形态
wait(50)
tap(other_switch_form.x, other_switch_form.y, 1)
wait(50)
tap(other_switch_form.x, other_switch_form.y, 1)

-- 4号植物种到第三行第三列
wait(100)
swipe(plant_slots[4].x, plant_slots[4].y, board[3][3].x, board[3][3].y,10)

-- 3号植物种到第三行第三列
wait(100)
swipe(plant_slots[3].x, plant_slots[3].y, board[3][3].x, board[3][3].y,10)

-- 6号植物分别种到第三行第三列、第三行第五列
wait(100)
swipe(plant_slots[6].x, plant_slots[6].y, board[3][3].x, board[3][3].y,10)
wait(100)
swipe(plant_slots[6].x, plant_slots[6].y, board[3][5].x, board[3][5].y,10)

-- 1号植物分别种到第一行第一列、第二行第一列
wait(30)
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y,30)
wait(30)
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y,30)

-- 并行执行：一路持续点击下一波，另一路检测最后一波红字后停止
parallel(
function()
while flag == 1 do
tap(other_next_wave.x, other_next_wave.y, 1)
end
end,
function()
while flag == 1 do
if  
check_color("#FF0000", 10, other_final_wave_red.x, other_final_wave_red.y)
  then
flag = 0
end
end
end
)
