# PVZ2 脚本与校准变量说明

这份文档给写脚本的 AI 使用。目标是让 AI 能正确生成 PVZ2 Lua 脚本，并能生成电脑端一键推送脚本，把脚本下载到手机 App。

## 基本规则

PVZ2 脚本使用 Lua 风格代码。脚本文件建议使用 UTF-8 无 BOM 保存，文件名建议为：

```text
pvz2_脚本名.lua
```

App 导入脚本时会把文件名前缀 `pvz2_` 去掉，作为手机里的脚本名。例如：

```text
pvz2_天空无尽.lua -> 手机脚本名：天空无尽
```

脚本里优先使用校准变量，不要写死屏幕坐标。点位变量结构：

```lua
变量.x
变量.y
```

区域变量结构：

```lua
区域.left
区域.top
区域.right
区域.bottom
```

校准变量只有在对应校准项保存后才会注册到 Lua 里。比如没有完成“无尽补给相关”校准时，
`endless_supply_...` 这一组变量不会存在。写脚本前应提醒用户先完成脚本会用到的校准项。

## 常用函数

| 函数 | 含义 |
|---|---|
| `tap(x, y)` | 点击坐标，默认按住 1ms |
| `tap(x, y, durationMs)` | 按住点击，第三个参数是按住毫秒数 |
| `swipe(x1, y1, x2, y2)` | 滑动，默认持续 300ms |
| `swipe(x1, y1, x2, y2, durationMs)` | 指定时长滑动 |
| `wait(ms)` | 等待毫秒 |
| `check_color("#RRGGBB", tolerance, x, y)` | 检测某点颜色是否匹配，`tolerance` 是 0-100 容差百分比 |
| `check_color_not("#RRGGBB", tolerance, x, y)` | 检测某点颜色是否不匹配，`tolerance` 是 0-100 容差百分比 |
| `check_text("文字", left, top, right, bottom)` | 检测区域内是否出现文字，推荐新脚本使用 |
| `check_text_not("文字", left, top, right, bottom)` | 检测区域内是否没有文字 |
| `parallel(function() ... end, function() ... end)` | 并行执行多个函数 |

文字检测会截取当前屏幕区域；支持的 arm64 真机优先使用离线 PaddleOCR，设备不适合或引擎不可用时会回退到 ML Kit。

示例：

```lua
tap(start_battle.x, start_battle.y)
wait(500)
swipe(plant_slots[1].x, plant_slots[1].y, board[3][5].x, board[3][5].y, 200)

if check_color("#FF0000", 10, final_wave_red.x, final_wave_red.y) then
    tap(other_next_wave.x, other_next_wave.y)
end

if check_text("最后一波", final_wave_text_area.left, final_wave_text_area.top, final_wave_text_area.right, final_wave_text_area.bottom) then
    tap(other_next_wave.x, other_next_wave.y)
end
```

## 全部 PVZ2 校准变量

### 植物卡槽

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `plant_slots` | 植物卡槽列表，包含第 1 到第 8 个卡槽 | 表 |
| `plant_slots[1]` | 第 1 个植物卡槽 | 点 |
| `plant_slots[2]` | 第 2 个植物卡槽 | 点 |
| `plant_slots[3]` | 第 3 个植物卡槽 | 点 |
| `plant_slots[4]` | 第 4 个植物卡槽 | 点 |
| `plant_slots[5]` | 第 5 个植物卡槽 | 点 |
| `plant_slots[6]` | 第 6 个植物卡槽 | 点 |
| `plant_slots[7]` | 第 7 个植物卡槽 | 点 |
| `plant_slots[8]` | 第 8 个植物卡槽 | 点 |
| `plant_slot_1` | 第 1 个植物卡槽 | 点 |
| `plant_slot_2` | 第 2 个植物卡槽 | 点 |
| `plant_slot_3` | 第 3 个植物卡槽 | 点 |
| `plant_slot_4` | 第 4 个植物卡槽 | 点 |
| `plant_slot_5` | 第 5 个植物卡槽 | 点 |
| `plant_slot_6` | 第 6 个植物卡槽 | 点 |
| `plant_slot_7` | 第 7 个植物卡槽 | 点 |
| `plant_slot_8` | 第 8 个植物卡槽 | 点 |

### 种植棋盘

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `board` | 种植棋盘二维表，格式是 `board[行][列]` | 表 |
| `board[1][1]` ~ `board[1][9]` | 第 1 行第 1 到第 9 列格子 | 点 |
| `board[2][1]` ~ `board[2][9]` | 第 2 行第 1 到第 9 列格子 | 点 |
| `board[3][1]` ~ `board[3][9]` | 第 3 行第 1 到第 9 列格子 | 点 |
| `board[4][1]` ~ `board[4][9]` | 第 4 行第 1 到第 9 列格子 | 点 |
| `board[5][1]` ~ `board[5][9]` | 第 5 行第 1 到第 9 列格子 | 点 |
| `board_r1_c1` ~ `board_r1_c9` | 第 1 行第 1 到第 9 列格子 | 点 |
| `board_r2_c1` ~ `board_r2_c9` | 第 2 行第 1 到第 9 列格子 | 点 |
| `board_r3_c1` ~ `board_r3_c9` | 第 3 行第 1 到第 9 列格子 | 点 |
| `board_r4_c1` ~ `board_r4_c9` | 第 4 行第 1 到第 9 列格子 | 点 |
| `board_r5_c1` ~ `board_r5_c9` | 第 5 行第 1 到第 9 列格子 | 点 |

### 阳光相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `sun_points` | 阳光相关点位表 | 表 |
| `sun_buy_key` | 购买阳关键 | 点 |
| `sun_ad` | 广告 | 点 |
| `sun_10_diamond` | 10 钻石 | 点 |
| `sun_close` | 关闭 | 点 |

### 能量豆相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `plant_food_points` | 能量豆相关点位表 | 表 |
| `plant_food_bean` | 豆 | 点 |
| `plant_food_plus` | 加号 | 点 |
| `plant_food_buy` | 购买 | 点 |

### 神器相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `artifact_points` | 神器相关点位表 | 表 |
| `artifact_main` | 神器 | 点 |
| `artifact_small` | 小 | 点 |
| `artifact_medium` | 中 | 点 |
| `artifact_large` | 大 | 点 |
| `artifact_switch` | 切换 | 点 |
| `artifact_gourd` | 葫芦 | 点 |
| `artifact_bowling` | 保龄球 | 点 |
| `artifact_equipment` | 装备 | 点 |
| `artifact_close` | 关闭 | 点 |

### 黄瓜相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `cucumber_points` | 黄瓜相关点位表 | 表 |
| `cucumber_main` | 黄瓜 | 点 |
| `cucumber_drop` | 下瓜 | 点 |
| `cucumber_close` | 关闭 | 点 |

### 充值相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `recharge_points` | 充值相关点位表 | 表 |
| `recharge_main` | 充值 | 点 |
| `recharge_close` | 关闭 | 点 |

### 战斗颜色文字

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `cards_points` | 战斗颜色文字点位表 | 表 |
| `cards_poker` | 扑克牌 | 点 |
| `final_wave_red` | 最后一波红色 | 点 |
| `final_wave_text_area` | 最后一波文字 | 区域 |

### 开始战斗相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `start_battle_points` | 开始战斗相关点位表 | 表 |
| `start_battle` | 开始战斗 | 点 |
| `card_start_battle` | 选卡开始战斗 | 点 |
| `start_battle_pair` | 配对 | 点 |
| `start_battle_deck_1` | 卡组1 | 点 |
| `start_battle_deck_2` | 卡组2 | 点 |
| `start_battle_deck_3` | 卡组3 | 点 |

### 其他位置

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `other_points` | 其他位置点位表 | 表 |
| `other_speed_up` | 加速 | 点 |
| `other_pause` | 暂停 | 点 |
| `other_continue` | 继续 | 点 |
| `other_restart` | 重新开始 | 点 |
| `other_back_to_map` | 返回地图 | 点 |
| `other_shovel` | 铲子 | 点 |
| `other_next_wave` | 下一波 | 点 |
| `other_switch_form` | 切换形态 | 点 |

### 无尽补给相关

| Lua 变量 | 中文意思 | 类型 |
|---|---|---|
| `endless_supply_points` | 无尽补给相关点位表 | 表 |
| `endless_supply_text_area` | 补给界面 | 区域 |
| `endless_supply_ability_areas` | 能力1/能力2/能力3区域表 | 表 |
| `endless_supply_ability_centers` | 能力1/能力2/能力3中心点表 | 表 |
| `endless_supply_ability_1_area` | 能力1 | 区域 |
| `endless_supply_ability_1` | 能力1中心点 | 点 |
| `endless_supply_ability_2_area` | 能力2 | 区域 |
| `endless_supply_ability_2` | 能力2中心点 | 点 |
| `endless_supply_ability_3_area` | 能力3 | 区域 |
| `endless_supply_ability_3` | 能力3中心点 | 点 |
| `endless_supply_blue_confirm` | 蓝色确定 | 点 |
| `endless_supply_green_confirm` | 绿色确定 | 点 |
| `endless_supply_final_confirm` | 最后确定 | 点 |
| `endless_supply_continue_challenge` | 继续挑战 | 点 |

能力区域变量表示能力识别框；能力中心点变量表示对应识别框的中心点。

## 可选 ADB 授权

如果是在自己的开发或测试设备上调试，可以通过 ADB 授权
`WRITE_SECURE_SETTINGS`，让 App 在具备权限时尝试恢复无障碍服务状态：

```shell
adb shell pm grant com.fffcccdfgh.androidclicker android.permission.WRITE_SECURE_SETTINGS
```

前提是 App 的 `AndroidManifest.xml` 已声明
`android.permission.WRITE_SECURE_SETTINGS`。执行一次后，只要不卸载 App，一般会
保留这个授权；App 启动时可以自动把自己的无障碍服务重新写回系统设置。

普通用户手动使用 App 时，不一定需要这条命令。不要在不了解风险的设备上授予
这个权限。

## 一键把脚本下载到手机

App 包名：

```text
com.fffcccdfgh.androidclicker
```

手机端同步目录：

```text
/sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync
```

手机 App 会在 PVZ2 脚本列表页轮询同步目录。电脑脚本推送完成后，用户打开或停留在 PVZ2 脚本列表页，App 会弹出确认窗口。

### 单个脚本同步协议

推送文件：

```text
/sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/pvz2.lua
/sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/pvz2.meta.json
```

`pvz2.lua` 是脚本内容。

`pvz2.meta.json` 示例：

```json
{
  "scriptName": "天空无尽",
  "updatedAt": "2026-06-14T12:00:00.0000000Z",
  "sourcePath": "E:\\pvz2\\天空\\天空无尽\\pvz2_天空无尽.lua"
}
```

字段含义：

| 字段 | 含义 |
|---|---|
| `scriptName` | 手机上的脚本名 |
| `updatedAt` | 更新时间，建议使用 UTC ISO 时间 |
| `sourcePath` | 电脑端源文件路径，用于生成变化签名 |

### 批量同步协议

批量同步目录：

```text
/sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/batch
```

必须包含 manifest：

```text
/sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/batch/pvz2_batch.json
```

`pvz2_batch.json` 示例：

```json
{
  "batchId": "20260614120000",
  "mode": "merge",
  "scripts": [
    {
      "name": "天空无尽",
      "path": "pvz2_天空无尽.lua"
    },
    {
      "name": "天空前50关",
      "path": "pvz2_天空前50关.lua"
    }
  ]
}
```

字段含义：

| 字段 | 含义 |
|---|---|
| `batchId` | 批次 ID。每次推送必须变化，否则 App 会认为已经处理过 |
| `mode` | `merge` 或 `replace_all` |
| `scripts` | 脚本列表 |
| `scripts[].name` | 手机上的脚本名 |
| `scripts[].path` | 相对 `batch` 目录的 Lua 文件路径 |

`mode` 的区别：

| mode | 手机端行为 |
|---|---|
| `merge` | 添加或覆盖同名脚本，不删除手机里其他脚本 |
| `replace_all` | 先删除手机里所有 PVZ2 脚本，再导入本批次脚本 |

电脑端推送脚本应该让用户选择同步模式：

```text
请选择同步模式：
[1] 更新全部（merge）：保留手机已有脚本，只更新/新增本批脚本。
[2] 清空并导入（replace_all）：平板确认后先清空已有 PVZ2 脚本，再导入本批脚本。
```

选择规则：

| 用户输入 | 写入 manifest 的 mode | 含义 |
|---|---|---|
| `1` | `merge` | 保留手机已有脚本，只新增或覆盖本批脚本 |
| `2` | `replace_all` | 平板确认后清空已有 PVZ2 脚本，再导入本批脚本 |

如果用户输入不是 `1` 或 `2`，脚本应该继续提示，直到输入有效值。

### 推荐 PowerShell 批量推送脚本

把下面内容保存为：

```text
E:\pvz2\天空\push_all_pvz2.ps1
```

`.ps1` 文件建议使用 UTF-8 with BOM 保存，避免 Windows PowerShell 5 读取中文路径或中文字符串出错。

```powershell
param(
    [ValidateSet("merge", "replace_all")]
    [string]$Mode = "merge"
)

$ErrorActionPreference = "Stop"

$Root = "E:\pvz2\天空"
$PackageName = "com.fffcccdfgh.androidclicker"
$RemoteSyncDir = "/sdcard/Android/data/$PackageName/files/sync"
$RemoteBatchDir = "$RemoteSyncDir/batch"

$adbCandidates = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
)
$adb = $adbCandidates | Where-Object { $_ -and (Test-Path -Path $_) } | Select-Object -First 1
if (-not $adb) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $adb = $adbCommand.Source
    }
}
if (-not $adb) {
    throw "adb.exe not found. Install Android platform-tools or add adb to PATH."
}

$deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "^\S+\s+device$" })
if ($deviceLines.Count -eq 0) {
    throw "No authorized Android device found. Connect device and allow USB debugging."
}

$devices = @($deviceLines | ForEach-Object { ($_ -split "\s+")[0] })
if ($devices.Count -eq 1) {
    $targetDevice = $devices[0]
} else {
    Write-Host "Connected devices:"
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host "[$($i + 1)] $($devices[$i])"
    }
    do {
        $choice = Read-Host "Select device number"
        $choiceNumber = 0
        $validChoice = [int]::TryParse($choice, [ref]$choiceNumber) -and
            $choiceNumber -ge 1 -and
            $choiceNumber -le $devices.Count
    } while (-not $validChoice)
    $targetDevice = $devices[$choiceNumber - 1]
}
$adbTarget = @("-s", $targetDevice)

if (-not (Test-Path -Path $Root)) {
    throw "Root folder not found: $Root"
}

$scripts = @()
foreach ($dir in Get-ChildItem -Path $Root -Directory) {
    $lua = Get-ChildItem -Path $dir.FullName -File -Filter "*.lua" |
        Where-Object { $_.Name -like "pvz2_*.lua" } |
        Select-Object -First 1
    if (-not $lua) {
        $lua = Get-ChildItem -Path $dir.FullName -File -Filter "*.lua" | Select-Object -First 1
    }
    if ($lua) {
        $name = [System.IO.Path]::GetFileNameWithoutExtension($lua.Name)
        if ($name.StartsWith("pvz2_")) {
            $name = $name.Substring(5)
        }
        $scripts += [pscustomobject]@{
            Name = $name
            Source = $lua.FullName
            FileName = $lua.Name
        }
    }
}

if ($scripts.Count -eq 0) {
    throw "No Lua scripts found under $Root"
}

$batchId = (Get-Date).ToUniversalTime().ToString("yyyyMMddHHmmssffff")
$tempBatch = Join-Path $env:TEMP "pvz2_batch_$batchId"
New-Item -Path $tempBatch -ItemType Directory -Force | Out-Null

$manifestScripts = @()
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

foreach ($script in $scripts) {
    $targetFile = Join-Path $tempBatch $script.FileName
    $content = [System.IO.File]::ReadAllText($script.Source)
    if ($content.Length -gt 0 -and $content[0] -eq [char]0xFEFF) {
        $content = $content.Substring(1)
    }
    [System.IO.File]::WriteAllText($targetFile, $content, $utf8NoBom)

    $manifestScripts += [pscustomobject]@{
        name = $script.Name
        path = $script.FileName
    }
}

$manifest = [pscustomobject]@{
    batchId = $batchId
    mode = $Mode
    scripts = $manifestScripts
}
$manifestPath = Join-Path $tempBatch "pvz2_batch.json"
$manifestJson = $manifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText($manifestPath, $manifestJson, $utf8NoBom)

Write-Host "Using adb: $adb"
Write-Host "Using device: $targetDevice"
Write-Host "Mode: $Mode"
Write-Host "Scripts: $($scripts.Count)"

& $adb @adbTarget shell "rm -rf '$RemoteBatchDir' && mkdir -p '$RemoteBatchDir'" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "adb shell failed."
}
& $adb @adbTarget push "$tempBatch\." $RemoteBatchDir | Out-Null

if ($LASTEXITCODE -ne 0) {
    throw "adb push failed."
}

Write-Host "Pushed batch $batchId to phone."
Write-Host "Open PVZ2 script list in the app and confirm the sync dialog."
```

### 推荐 BAT 启动脚本

把下面内容保存为：

```text
E:\pvz2\天空\推送全部-添加或覆盖.bat
```

```bat
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push_all_pvz2.ps1" -Mode merge
pause
```

把下面内容保存为：

```text
E:\pvz2\天空\推送全部-替换手机全部.bat
```

```bat
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0push_all_pvz2.ps1" -Mode replace_all
pause
```

### 一键推送脚本故障记录

如果电脑同时连接了真机和模拟器，不能直接执行 `adb shell` 或 `adb push`。否则 ADB 会报：

```text
more than one device/emulator
```

推送脚本必须先从 `adb devices` 里选出目标设备，然后所有 ADB 命令都带上 `-s <设备序列号>`。如果检测到多台设备，就列出来让用户选择。

脚本还必须检查每一条 ADB 命令的退出码。不要只在最后打印“完成”，否则可能出现实际推送失败、脚本却显示成功的情况。

每次推送批量脚本前，先重建手机端 batch 目录，避免旧 Lua 或旧 manifest 残留：

```powershell
& $adb @adbTarget shell "rm -rf '$RemoteBatchDir' && mkdir -p '$RemoteBatchDir'"
if ($LASTEXITCODE -ne 0) {
    throw "adb shell failed."
}
```

分目录一键脚本也要遵守同样规则，例如：

```text
E:\pvz2\天空\天空无尽\一键下载.bat
E:\pvz2\天空\天空无尽\push_batch.ps1
```

`push_batch.ps1` 里不要裸调用 `adb shell`、`adb push`。统一封装成带设备参数和失败检查的调用：

```powershell
$adbTarget = @("-s", $targetDevice)

function Invoke-AdbChecked([string[]]$AdbArgs) {
    & $adb @adbTarget @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "ADB command failed: adb $($adbTarget -join ' ') $($AdbArgs -join ' ')"
    }
}
```

如果 `.ps1` 使用中文提示语，文件必须保存为 UTF-8 with BOM；否则 Windows PowerShell 5 可能把中文字符串解析坏。为了更稳，推送脚本里的提示语可以直接使用英文或 ASCII。

推送后可以用下面命令确认手机端目录真的有新 batch：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <设备序列号> shell ls -l /sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/batch
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <设备序列号> shell cat /sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/batch/pvz2_batch.json
```

## 给脚本 AI 的生成要求

1. 生成 Lua 脚本时，优先使用本文档里的校准变量。
2. Lua 文件使用 UTF-8 无 BOM。
3. PowerShell 文件如果包含中文路径或中文字符串，使用 UTF-8 with BOM。
4. 批量推送时，`batchId` 每次必须变化。
5. 用户只想添加或覆盖脚本时，用 `mode = "merge"`。
6. 用户想删除手机旧脚本并只保留本批次脚本时，用 `mode = "replace_all"`。
7. 所有 `adb shell`、`adb push` 都必须带 `-s <设备序列号>`，不能裸调用 `adb`。
8. 每条 ADB 命令执行后都必须检查 `$LASTEXITCODE`，失败就 `throw`，不能继续显示完成。
9. 推送前必须重建 `/sdcard/Android/data/com.fffcccdfgh.androidclicker/files/sync/batch`。
10. 电脑同时连接多个设备时，让用户选择目标设备。
