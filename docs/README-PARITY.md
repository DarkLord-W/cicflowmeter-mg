# README parity

`README.md` (English, default) and `README.zh-CN.md` (Chinese) are meant to stay
in step. Both have the same sections in the same order and reference the same
images. When one changes, change the other.

| # | `README.md` | `README.zh-CN.md` |
|---|---|---|
| 1 | What this fork changes | 本分支改了什么 |
| 2 | Time-rule labels | 时间规则标签 |
| 3 | Live feature streaming | 实时特征上报 |
| 4 | Health endpoint | 健康检查 |
| 5 | Command line | 命令行 |
| 6 | Requirements | 环境要求 |
| 7 | Install | 安装 |
| 8 | Running | 运行 |
| 9 | Screens | 界面截图 |
| 10 | Docker and Podman | Docker 与 Podman |
| 11 | systemd | systemd |
| 12 | Configuration | 配置 |
| 13 | Troubleshooting | 常见问题 |
| 14 | Known limitations | 已知限制 |
| 15 | Build and test | 构建与测试 |
| 16 | Project layout | 目录 |
| 17 | Upstream, credits and license | 上游、致谢与许可 |
| 18 | Versioning | 版本 |

Both files reference the 15 images in `docs/images/` and use the same
`docs/` links. A quick check:

```bash
grep -c '^## ' README.md README.zh-CN.md          # equal counts
grep -o 'docs/images/[a-z-]*\.png' README.md | sort -u
grep -o 'docs/images/[a-z-]*\.png' README.zh-CN.md | sort -u
```
