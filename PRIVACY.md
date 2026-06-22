# 隐私说明

Click 设计为在设备本地运行的 Android 自动化工具。App 不申请 Android 网络权限，也不会主动上传脚本、截图、OCR 结果或校准数据。

## 本地处理的数据

App 可能会在设备本地处理以下数据：

- 通过 Android MediaProjection 捕获的屏幕画面，用于 OCR 和颜色匹配。
- 用于发送点击和滑动的无障碍手势状态。
- 已保存的普通脚本和 PVZ2 脚本。
- PVZ2 校准点位和区域。
- 放在 App 专属外部文件目录里的 USB 同步文件。
- OCR 调试裁剪图，仅在开发构建显式启用调试图片保存时产生。

## 本地存储

已保存的脚本和校准数据会存储在 Android 的 App 私有数据中。USB 同步文件和 OCR 调试图片可能会存储在 App 专属外部文件目录中，例如：

```text
Android/data/com.fffcccdfgh.androidclicker/files/
```

OCR 调试裁剪图默认关闭。如果某个开发构建启用了它，调试裁剪图会保存在 App 专属外部文件区域内的本地 `Pictures/ocr-debug` 目录。这些图片可能包含你正在自动化的屏幕内容。

## 权限

App 会请求一些较强的 Android 能力：

- 无障碍服务：发送点击和滑动手势。
- 悬浮窗权限：在其他 App 上方显示控制面板。
- 屏幕捕获权限：读取屏幕画面，用于 OCR 和颜色匹配。
- 前台服务权限：保持屏幕捕获和悬浮控制运行。
- 可选的 `WRITE_SECURE_SETTINGS`：在开发/测试设备上通过 ADB 授权后，可用于恢复无障碍状态。

只有在你理解 App 行为并信任已安装构建时，才应授予这些权限。

## 删除本地数据

要删除本地数据，可以在 Android 系统设置中清除 App 存储，或卸载 App。如果 OCR 调试图片或 USB 同步文件写入了 App 专属外部文件区域，正常 Android 设备在卸载 App 时也会删除该目录。

## 第三方 SDK

App 内置 PaddleOCR、Paddle Lite、OpenCV 组件，并依赖 Google ML Kit 作为备用 OCR。组件详情见 `THIRD_PARTY_NOTICES.md`。
