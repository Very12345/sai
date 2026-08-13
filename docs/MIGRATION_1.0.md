# PhoneAgent 1.0 升级说明

- APK 使用与 0.1 测试版相同的应用 ID 和签名，可直接覆盖安装。
- Room 数据库从 v1 无损迁移到 v2，保留工作区、会话、模型配置和加密 API Key。
- 旧默认工作区继续使用原私有目录；新建项目位于 `workspaces/Project/项目名`。
- 已安装 Debian、Node.js 和项目文件不会在升级时移动或重新下载。
- 进程被系统回收时，运行中的任务转为“已暂停，可恢复”；恢复使用最近检查点。
- 新增麦克风、MediaProjection 和 Accessibility 能力均为按需授权，默认不启用。
