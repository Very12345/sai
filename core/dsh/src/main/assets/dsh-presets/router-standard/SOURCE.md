# Vendored source

- Project: `yjh051108/dsh-router-standard`
- Source: https://github.com/yjh051108/dsh-router-standard
- Suite: https://github.com/yjh051108/dsh-routing-suite
- Commit: `d4655d5874883c6994721236f0ece97499570eac`
- Preset version: `0.1.1`
- License: MIT
- Vendored: 2026-08-15

Only the task-routing preset is bundled. The separate `dsh-super-injector`
runtime injection component is intentionally not bundled because it can load
and mutate arbitrary runtime plugins outside sai's reviewed install and
approval boundary.
