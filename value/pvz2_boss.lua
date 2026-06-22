-- AndroidClicker Lua Script
-- name: boss
-- loopCount: 1
-- loopGapMs: 0

wait(1)
-- AndroidClicker Lua Script
-- name: 自定义脚本
-- loopCount: 1
-- loopGapMs: 0

wait(1)

-- 识别扑克牌开始
while not check_color("#DF58FC", 10, cards_edge.x, cards_edge.y) do
wait(1)
end

wait(100)  -- 识别后延时100ms

-- 给豆下面那个大哥（第四行第五列）
swipe(plant_food_bean.x, plant_food_bean.y, board[4][5].x, board[4][5].y, 30)
wait(100)

-- 给豆刚刚那个大哥左边一个植物（第四行第四列）
swipe(plant_food_bean.x, plant_food_bean.y, board[4][4].x, board[4][4].y, 30)
wait(100)

-- 开加速
tap(other_speed_up.x, other_speed_up.y, 1)
wait(100)

-- 种植能量花 x2 到第五行第二列和第三列
swipe(plant_slots[1].x, plant_slots[1].y, board[5][2].x, board[5][2].y, 30)
wait(100)
swipe(plant_slots[1].x, plant_slots[1].y, board[5][3].x, board[5][3].y, 30)

-- 结束脚本
