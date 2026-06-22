-- AndroidClicker Lua Script
-- name: 保龄球 后期
-- loopCount: 1
-- loopGapMs: 0

wait(1)
-- AndroidClicker Lua Script
-- name: 后期 保龄球
-- loopCount: 1
-- loopGapMs: 0

wait(1)
wait(1)
flag = 1

wait(10)
while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
end

wait(1)
tap(other_speed_up.x, other_speed_up.y, 1)
wait(50)
-- 球果：能量豆 → 第二行第二列
swipe(plant_food_bean.x, plant_food_bean.y, board[2][2].x, board[2][2].y, 10)
wait(50)
-- 种植第二个植物（2号卡槽）→ 第三行第九列
swipe(plant_slots[2].x, plant_slots[2].y, board[3][9].x, board[3][9].y, 10)
wait(50)
-- 再次给豆：能量豆 → 第三行第九列
swipe(plant_food_bean.x, plant_food_bean.y, board[3][9].x, board[3][9].y, 10)
wait(50)
-- 能量花1：1号植物 → 第五行第二列
swipe(plant_slots[1].x, plant_slots[1].y, board[5][2].x, board[5][2].y, 10)
wait(50)
-- 能量花2：1号植物 → 第五行第三列
swipe(plant_slots[1].x, plant_slots[1].y, board[5][3].x, board[5][3].y, 10)
wait(300)
-- 神器（仅点击一次）
tap(artifact_main.x, artifact_main.y, 10)
wait(1100)

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
-- 给豆：能量豆 → 第三行第五列
swipe(plant_food_bean.x, plant_food_bean.y, board[3][5].x, board[3][5].y, 10)
end
end
end
)
