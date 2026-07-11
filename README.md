# 身刻

记录身体变化，掌控生活节奏。

身刻是训练计划、训练记录和身体状态工具。它与独立项目 `home-training-timer` 共用 Cloudflare Worker + D1 和同一套数据契约，但保持不同职责与部署。

## 产品边界

- 身刻：计划、日历、正式训练记录、身体状态、趋势、反馈摘要和同步协调。
- home-training-timer：routine 执行、动作提示、语音、常亮和 `timer_sessions`。
- Worker + D1：认证、角色权限、实体校验、冲突元数据和共享云端副本。

两个前端不合并仓库。双方可读取共享记录，但只写自己拥有的实体。

## 当前技术形态

- Web：静态 HTML/CSS/JavaScript，后续渐进迁移到 Vite + TypeScript 模块。
- 本地：IndexedDB 第一写入点，离线可查看、记录和执行缓存 routine。
- 云端：Cloudflare Worker + D1，使用通用 record envelope。
- 部署：GitHub Pages。
- Android：后续使用共享领域层和独立移动 UI，不缩放桌面版页面。

## 关键文档

- [共享数据契约](docs/data-contract.md)
- [系统设计](docs/system-design.md)
- [下一阶段开发方案](docs/next-stage-development-plan.md)
- [开发约束边界](docs/development-constraints.md)
- [移动端策略](docs/mobile-strategy.md)
- [云数据库部署](docs/cloudflare-cloud-db-setup.md)
- [Cloud Records API](docs/cloud-records-api.md)

## 开发原则

- 本地优先，云端失败不能阻止记录。
- 计划、调整、计时事实和正式训练记录分层保存。
- 历史日期保存当时快照，不被后续模板升级重写。
- token、真实健康导出、截图和临时文件不得提交仓库。
- 所有跨端字段先进入数据契约，再进入实现。
