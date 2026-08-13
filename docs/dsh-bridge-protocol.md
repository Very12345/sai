# sai / DeepSeek Harness bridge protocol v1

The bridge listens on loopback by default. A paired desktop may expose the same endpoint over the
existing authenticated WSS tunnel. Every request carries a short-lived bearer token bound to one
pairing and one active sai task.

`POST /v1/tools/call`

```json
{ "operation": "observe_device", "payload": {} }
```

The response is UTF-8 JSON or text limited to 64 KiB. Device observations and web text are marked
untrusted by the Android bridge. The plugin never makes approval decisions: sai checks the task's
capability grant and either executes, queues a phone approval, or rejects the call.

Stable operations in v1 are `observe_device`, `device_action`, `browser`, `speak`, `notify`,
`attach_file`, and `task_status`. New operations require a protocol version bump rather than
silently changing an existing operation's meaning.
