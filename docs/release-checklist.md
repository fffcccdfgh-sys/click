# 发布和公开仓库检查清单

在公开仓库或发布 APK 前使用这份清单。

## 仓库可见性

- 确认本地 `origin` remote 指向目标仓库。
- 准备发布期间，确认目标仓库仍然是 private。
- 使用 `git status --short` 和 `git diff` 检查所有未提交改动。
- 下面的检查完成前，不要修改仓库可见性。

## 密钥和私有文件

- 确认这些文件没有被 Git 跟踪：
  - `key`
  - `local.properties`
  - `.env` 和 `.env.*`
  - 签名密钥、证书、密码、token、日志、APK 和 AAB
- 运行：

  ```powershell
  git ls-files | Where-Object { $_ -match '(?i)(^|/)(key|local\.properties|.*\.env.*|.*token.*|.*secret.*|.*password.*|.*credential.*|auth\.json|credentials\.json)$' }
  ```

- 预期结果：没有输出。
- 如果真实密钥曾经被提交过，公开前需要轮换或替换。

## 构建验证

- 运行单元测试：

  ```powershell
  .\gradlew.bat test
  ```

- 构建 debug APK：

  ```powershell
  .\gradlew.bat assembleDebug
  ```

- 如果构建 release APK，使用 `local.properties` 中已有的 release 签名配置；不要提交或打印签名密码。

## 文档

- `README.md` 说明项目、权限、构建步骤和脚本文档。
- `LICENSE` 存在。
- `NOTICE` 存在。
- `THIRD_PARTY_NOTICES.md` 说明随仓库提供的 native 库、模型文件和主要依赖。
- `docs/pvz2-calibration-guide.md` 说明 PVZ2 校准流程，并链接可选示例截图包。
- `PRIVACY.md` 说明本地屏幕捕获、OCR、脚本、存储和调试图片。
- GitHub issue 模板存在。

## 手动冒烟测试

- 在测试设备上安装 debug APK。
- 打开 App。
- 授予无障碍、悬浮窗和屏幕捕获权限。
- 启动悬浮面板。
- 添加一个点击或等待动作。
- 运行并停止脚本。
- 打开脚本列表，确认导入/导出仍然可用。
- 如果测试 PVZ2 流程，确认校准界面可以打开，并且已保存的校准值可以加载。

## 发布注意事项

- 公开仓库后，任何人都可以查看和 fork。
- 如果之后再把仓库改回 private，已经存在的公开 fork 或本地 clone 仍可能继续存在。
- 发布 APK 前，请在用于该 APK 的准确提交上重新跑完同一份检查清单。
