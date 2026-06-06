-- AndroidClicker Lua Script
-- name: 第三关
-- loopCount: 1
-- loopGapMs: 0

wait(1)
-- AndroidClicker Lua Script
-- name: 第三关
-- loopCount: 1
-- loopGapMs: 0

wait(1)
flag=1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
wait(1)
end

wait(10)
tap(other_speed_up.x, other_speed_up.y, 1)

wait(100)
-- 给豆球果（能量豆到球果位置 board[3][3]）
swipe(plant_food_bean.x, plant_food_bean.y, board[3][3].x, board[3][3].y,100)

wait(100)
-- 融合电大（2号植物到大哥位置 board[2][4]）
swipe(plant_slots[2].x, plant_slots[2].y, board[2][4].x, board[2][4].y,100)
wait(100)
-- 融合电大（2号植物到大哥位置 board[4][5]）
swipe(plant_slots[2].x, plant_slots[2].y, board[4][5].x, board[4][5].y,100)

wait(100)
-- 给豆牛蒡（能量豆到牛蒡位置 board[3][5]）
swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y,100)

wait(100)
-- 能量花（1号植物到第一行第一列）
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y,100)
wait(100)
-- 能量花（1号植物到第二行第一列）
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y,100)

wait(100)

parallel(
function()
while flag == 1 do
tap(other_next_wave.x, other_next_wave.y, 1)
end
end,
function()
while flag == 1 do
if check_color("#FF0000", 10, other_final_wave_red.x, other_final_wave_red.y) then
flag = 0
end
end
end
)
