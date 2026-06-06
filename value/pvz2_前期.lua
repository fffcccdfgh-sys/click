-- AndroidClicker Lua Script
-- name: 前期
-- loopCount: 1
-- loopGapMs: 0

wait(1)
-- AndroidClicker Lua Script
-- name: 前期
-- loopCount: 1
-- loopGapMs: 0

wait(1)
flag = 1

while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
wait(1)
end

tap(other_speed_up.x, other_speed_up.y, 1)

wait(100)
-- 球果（给豆）
swipe(plant_food_bean.x, plant_food_bean.y, board[3][3].x, board[3][3].y,100)

wait(100)
-- 能量花1
swipe(plant_slots[1].x, plant_slots[1].y, board[1][1].x, board[1][1].y,100)

wait(100)
-- 牛蒡（给豆）
swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y,100)

wait(100)
-- 能量花2
swipe(plant_slots[1].x, plant_slots[1].y, board[2][1].x, board[2][1].y,100)

wait(60)
-- 神器
tap(artifact_main.x, artifact_main.y, 1)
-- 大（神器大）
tap(artifact_large.x, artifact_large.y, 1)

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
