# 身刻

记录身体变化，掌控生活节奏。

身刻是训练与身体状态记录工具。它负责日历、训练记录、身体指标、状态反馈、截图识别占位、天气和趋势查看。`home-training-timer` 继续作为独立计时器项目存在，负责训练执行、动作提示、常亮和训练会话输出。

## 架构边界

- 身刻：计划、日历、记录、身体指标、反馈摘要。
- home-training-timer：计时器、动作流程执行、timer session。
- Cloudflare Worker + D1：统一云数据库 API，按角色控制读写。

两个前端不合并代码，也不互相擅自修改对方负责的数据；它们通过同一套数据结构和同一个 D1 数据库协作。

## 当前形态

- 前端：静态 HTML/CSS/JS。
- 本地存储：IndexedDB，失败时回退到 localStorage。
- 云端：Cloudflare Worker + D1，使用通用 `cloud_records` JSON envelope。
- 部署：可放 GitHub Pages / Cloudflare Pages。
- Android：后续可用 WebView 壳封装。

## 云数据库

Worker API 位于 `cloudflare/worker.js`，D1 migration 位于 `cloudflare/migrations/0001_cloud_records.sql`。

角色令牌：

- `ADMIN_TOKEN`: 维护用，全量读写。
- `SHENK_TOKEN`: 身刻写计划、日志、身体指标等记录。
- `TIMER_TOKEN`: timer 写训练执行会话。

详细部署见 [Cloudflare D1 cloud database setup](docs/cloudflare-cloud-db-setup.md)。

## 开发原则

- 本地记录优先，云端失败不能阻塞记录。
- 计划、调整和实际完成分层保存。
- 旧日期保留当时的计划快照，不被后续计划变化重写。
- 真实健康数据、截图、导出文件、token 不提交到仓库。
