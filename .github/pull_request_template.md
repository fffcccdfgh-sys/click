## 概要

-

## 测试

- [ ] `.\gradlew.bat test`
- [ ] `.\gradlew.bat assembleDebug`
- [ ] 如果改动涉及 UI、手势、OCR、屏幕捕获或 PVZ2 流程，已做真机手动测试

## 安全检查

- [ ] 没有提交 `local.properties`、签名密钥、密码、token、日志、APK 或私有截图。
- [ ] 如果改动影响权限、屏幕捕获、Lua 执行、存储或数据处理，已更新 `PRIVACY.md`。
- [ ] 如果改动了随仓库提供的库、native 二进制文件、模型文件或第三方依赖，已更新 `THIRD_PARTY_NOTICES.md`。
- [ ] 已考虑导入脚本或自动化行为是否可能伤害用户，或违反其他 App 的规则。
