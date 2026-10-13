# 弈境 YiJing

方寸之间，安心下一盘棋。一个使用 Kotlin 与 Jetpack Compose 开发的 Android 棋类应用，支持中国象棋、围棋和国际象棋，本地对弈、无广告。

[下载 Release](https://github.com/GodBook/yijing-chess/releases/latest) · [更新说明](docs/releases/v1.3.0.md) · [升级规划](docs/用户体验优化与升级规划.md) · [执行与验收记录](docs/执行记录.md)

## v1.3.0 的主要功能

- 人机、双人同屏和独立摆谱草稿；可选择执子方、AI 难度及每步思考上限。
- 自动保存完整棋局，大厅一键继续；进程重启后保留轮次、历史、围棋劫点及停着次数。
- 多棋局保存、命名、缩略图、棋类／状态／时间筛选，删除后可撤销。
- 悔棋、走步跳转、分支推演与局面注释，棋盘和导出棋谱使用同一份历史。
- 国际象棋 PGN／SAN、中国象棋 PGN／ICCS、围棋 SGF 导入导出；FEN 局面输入；系统文件保存与分享。
- 围棋 9／13／19 路，19 路默认提供落点预览；双停后标记死子，按所选规则与贴目估算并确认结果。
- 浅色／深色／跟随系统、高对比棋盘、坐标、音效、震动、动效与棋盘无障碍语义。
- 三道精选入门练习、基本操作及规则说明。
- GitHub 更新检查、系统后台下载、包完整性与签名校验、安装授权引导和分阶段重试。

## 规则与棋谱范围

| 棋类 | 已支持 | 边界 |
| --- | --- | --- |
| 中国象棋 | 飞将、别马腿、塞象眼、炮架、九宫、将死与困毙判负 | 复杂长将／长捉由双方协商；PGN 使用明确标记的 ICCS 坐标约定 |
| 国际象棋 | 王车易位、吃过路兵、四种升变、SAN 消歧与将死标记；将死、困毙、子力不足；三次重复／50 回合申请和棋，五次重复／75 回合自动和棋 | 启发式 AI 的难度名称不对应认证等级或 ELO |
| 围棋 | 禁自杀、单劫、提子、停着；中国数子／日本数目估算、贴目、手动死子标记 | 双活等争议由双方确认或继续对弈；不提供自动死活裁决或超级劫规则 |

SGF 支持 FF[4]、根节点摆子、行棋方、停着、分支和注释；中途重新摆棋／更改轮次会说明暂不支持。弈境使用可忽略的 `YJ` 扩展属性保留从局面开局时的劫点、提子、停着及恢复对弈信息。PGN 与 SGF 默认导出当前实战主线，可选择其他历史分支。FEN 导出当前查看局面。

v1.1.0 存档会保留原数据并迁移。能验证的历史会恢复为可撤销事件；旧围棋档未记录停着和劫点，会保留盘面、提子数及旧走步参考，并说明恢复边界。

## 系统与构建

- 最低 Android 11（API 30）。
- 目标 Android 15（API 35），编译 SDK 36。
- 使用 JDK 17 与仓库内 Gradle Wrapper；Android SDK 路径写入未跟踪的 `local.properties`，JDK 通过本机 `JAVA_HOME` 配置。

```bash
./gradlew :app:verifyChessRules :app:lintRelease :app:assembleRelease
```

正式 APK 位于 `app/build/outputs/apk/release/app-release.apk`。Release 开启 R8 与资源压缩，并保留存档字段名及既有覆盖安装签名。验证入口自动发现全部 `*Test` 单元测试，生成 JUnit XML 到 `app/build/test-results/verifyChessRules/`。

规则验证还会生成实际导出的棋谱样本；使用独立阅读器检查 SAN、最终局面、分支、SGF 摆子、停着与转义：

```bash
python -m pip install python-chess==1.999 chess==1.11.2 sgfmill==1.1.1
python scripts/verify-notation.py
```

```bash
adb devices -l
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 真机测试

连接并解锁 Android 手机，先构建 Release 和测试 APK。在一个终端启动仅监听回环地址的测试安装包服务，在另一个终端执行测试：

```bash
node scripts/qa-apk-server.mjs app/build/outputs/apk/release/app-release.apk 37123
```

```bash
adb reverse tcp:37123 tcp:37123
./gradlew :app:connectedDebugAndroidTest
adb reverse --remove tcp:37123
```

该服务仅用于验证系统 DownloadManager；HTTP 回环访问仅在 Debug 中开放，正式应用更新使用 HTTPS。测试使用独立存储名称与明确创建的 QA 棋局，不清空用户棋局库。测试完成后停止服务。

Windows 下可使用 `scripts/DeviceSnapshot.ps1` 保存实际截图及 UI 层级，使用 `scripts/DeviceTapText.ps1` 根据当前可见文字操作手机。自动测试结果与设备验收详见[执行记录](docs/执行记录.md)。

## 发布流程

PR、main 推送与标签会执行单元回归、lint、正式构建及测试 APK 编译。版本标签必须与 APK 版本一致；CI 生成 APK 和 SHA-256 文件，并创建草稿 Release。维护者下载草稿中的实际 APK，在设备上覆盖安装验收后再公开发布，确保发布的是验收过的文件。

## 许可证

沿用原项目的 MIT 开源声明。
