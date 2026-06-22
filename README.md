# Click

Click 是一款 Android 自动化工具，用于录制、编辑、导入并运行点击/滑动脚本。它面向 Android 设备本地使用，结合了无障碍手势、悬浮控制、屏幕捕获、OCR、颜色匹配和 Lua 脚本能力。

当前 App 界面和脚本文档以中文为主。

## 功能

- 悬浮控制面板：创建并运行点击、滑动、等待和编程动作。
- Lua 脚本导入/导出，支持按屏幕百分比记录坐标。
- 条件执行，支持 OCR 文本匹配和颜色匹配。
- 内置 PaddleOCR 资源，在 `arm64-v8a` 设备上进行本地 OCR；可用时也支持 ML Kit 作为备用 OCR。
- 基于屏幕捕获的 OCR 和颜色检测。
- PVZ2 专用脚本列表、校准流程和 USB 脚本同步辅助功能。

本项目与 Plants vs. Zombies 2、PopCap、EA 或其他游戏发行方没有关联。请负责任地使用自动化，并遵守被自动化应用或服务的规则。

## 仓库内容

- `app/src/main/java/` - Android App 源码。
- `app/src/main/cpp/` - 原生 PaddleOCR 桥接代码和随仓库提供的 native 头文件。
- `app/src/main/assets/paddleocr/` - 内置 PaddleOCR 模型资源。
- `app/src/main/jniLibs/` - `arm64-v8a` 运行时 native 库。
- `docs/normal-programming-guide.md` - 普通 Lua 脚本说明。
- `docs/pvz2-calibration-guide.md` - PVZ2 校准教程和可选示例截图包。
- `docs/pvz2-calibration-variables.md` - PVZ2 校准变量参考。

仓库有意保留 native 库和 OCR 模型文件，这样新克隆项目后更容易直接构建和运行。

## 环境要求

- Android Studio，以及较新的 Android Gradle Plugin 工具链。
- JDK 17 或更新版本。
- Android SDK，compile SDK 36。
- CMake 3.22.1。
- Android 8.0+ 设备或模拟器，`minSdk 26`。
- 支持 `arm64-v8a` 的设备，用于内置 native OCR 路径。

项目已包含 Gradle Wrapper，请优先使用仓库里的 wrapper，而不是系统全局 Gradle。

## 构建

克隆仓库后，用 Android Studio 打开项目，让 Android Studio 生成本地 `local.properties`，然后从 IDE 或终端构建：

```powershell
.\gradlew.bat assembleDebug
```

macOS 或 Linux：

```sh
./gradlew assembleDebug
```

运行单元测试：

```powershell
.\gradlew.bat test
```

发布签名由维护者在本地管理。release keystore 文件 `key` 和 `local.properties` 里的密码值会被 Git 忽略，不能提交到仓库。Fork 项目后请创建自己的签名密钥。

## 基本使用

1. 在 Android 设备上安装 debug 或 release APK。
2. 打开 App，并授予需要的能力：
   - 无障碍服务：用于发送点击和滑动手势。
   - 悬浮窗权限：用于显示控制面板。
   - 屏幕捕获权限：用于 OCR 和颜色检测。
3. 启动悬浮控制面板。
4. 录制动作，或导入 Lua 脚本。
5. 运行脚本，并通过悬浮控制或通知停止脚本。

脚本语法见：

- [普通脚本说明](docs/normal-programming-guide.md)
- [PVZ2 校准教程](docs/pvz2-calibration-guide.md)
- [PVZ2 校准变量](docs/pvz2-calibration-variables.md)

只运行你信任的脚本。导入的 Lua 脚本可以自动执行触摸操作，并且会在 App 进程内使用较完整的 Lua 运行环境。

## 权限和隐私

Click 不申请 Android 网络权限。屏幕画面、OCR 结果、脚本和校准数据都在设备本地处理。

因为 App 会使用较强的 Android 能力，安装或分发构建前建议阅读隐私说明：

- [隐私说明](PRIVACY.md)

## 第三方软件

本仓库包含第三方源码、头文件、模型和 native 库。归属和许可证说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 问题和维护

- Bug 反馈和功能建议请使用 GitHub Issues。
- 维护者在公开仓库或发布 APK 前，可以参考 [docs/release-checklist.md](docs/release-checklist.md)。

## 许可证

项目自有代码使用 Apache License 2.0。第三方组件仍然遵循各自的许可证。

Copyright 2026 fffcccdfgh-sys.
