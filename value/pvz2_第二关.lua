-- AndroidClicker Lua Script
-- name: 第二关
-- loopCount: 1
-- loopGapMs: 0

wait(1)
-- AndroidClicker Lua Script
-- name: 第二关
-- loopCount: 1
-- loopGapMs: 0

wait(1)
flag=1
wait(1)
while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
wait(1)
end--识别扑克牌开始

wait(1)
tap(other_speed_up.x, other_speed_up.y, 1)

wait(100)
-- 给豆大哥：从能量豆滑动到第二行第四列
swipe(plant_food_bean.x, plant_food_bean.y, board[2][4].x, board[2][4].y,100)

wait(100)
-- 给豆大哥：从能量豆滑动到第四行第五列
swipe(plant_food_bean.x, plant_food_bean.y, board[4][5].x, board[4][5].y,100)

wait(100)
-- 给豆守卫菇：从能量豆滑动到第三行第四列
swipe(plant_food_bean.x, plant_food_bean.y, board[3][4].x, board[3][4].y,100)

wait(100)
-- 能量花（第1植物）到第一行第一列
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y,100)

wait(100)
-- 能量花（第1植物）到第二行第一列
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y,100)

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
