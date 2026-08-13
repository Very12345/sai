# PhoneAgent 1.0 权限说明

- `INTERNET`：模型、Web、市场和远程 MCP。
- `RECORD_AUDIO`：用户主动启动系统语音识别时使用。
- `FOREGROUND_SERVICE_DATA_SYNC`：长任务和本地工具执行的可见通知。
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`：用户每次批准后的单次按需截图。
- SAF：导入/导出外部目录；应用私有项目无需存储权限。
- “所有文件访问”：可选，首版侧载场景使用；不是正常项目工作所必需。
- AccessibilityService：默认关闭，仅用户在系统设置手动开启并授权目标 App 后可用。
