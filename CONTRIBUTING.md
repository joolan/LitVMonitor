# 贡献指南

感谢你对 LitVMonitor 的关注！本文档将帮助你快速上手项目开发。

## 环境准备

| 工具 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | 推荐 Microsoft OpenJDK 17 |
| Maven | 3.9+ | 构建后端 |
| Node.js | 18+ | 构建前端 |
| npm | 9+ | 前端依赖管理 |

## 项目结构

```
LitVMonitor/
├── litv-monitor-server/          # 后端 (Spring Boot + MyBatis-Plus + SQLite)
│   ├── src/main/java/            # Java 源码
│   │   └── com/litv/monitor/     # 主包
│   ├── src/main/resources/       # 配置 & 迁移脚本
│   └── pom.xml                   # Maven 配置
├── litv-monitor-web/             # 前端 (Vue 3 + Vite + Element Plus)
│   ├── src/                      # 源码
│   └── package.json              # npm 配置
└── docs/                         # 项目文档
```

## 快速启动

```bash
# 后端
cd litv-monitor-server
mvn package -DskipTests
cd litv-monitor-server   # 进入 litv-monitor-server 子目录
java -jar target/litv-monitor-1.3.0.jar

# 前端（新终端）
cd litv-monitor-web
npm install
npm run dev
```

访问 http://localhost:5173，首次登录使用随机生成的管理员密码（查看启动日志）。

## 开发规范

### 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
feat: 新增告警渠道支持
fix: 修复登录页深色模式样式
docs: 更新操作手册
refactor: 重构监控执行器
```

### 代码风格

- **Java**：遵循 Google Java Style，4 空格缩进
- **Vue/JS**：遵循项目 `.eslintrc.cjs` 和 `.prettierrc` 规则，提交前执行 `npm run lint`（如已配置）
- **数据库**：新表需在 `src/main/resources/db/migration/` 添加迁移脚本，命名格式 `V{编号}__描述.sql`

### 分支管理

- `main` — 稳定分支，只接受 PR 合并
- `feat/*` — 功能开发分支
- `fix/*` — 修复分支

## 提交 PR

1. Fork 本仓库
2. 创建功能分支：`git checkout -b feat/my-feature`
3. 提交代码：`git commit -m "feat: xxx"`
4. Push 到你的 Fork：`git push origin feat/my-feature`
5. 发起 Pull Request，说明改动内容

## 问题反馈

- 通过 [GitHub Issues](https://github.com/joolan/LitVMonitor/issues) 提交 Bug 或功能建议
- 提交 Issue 时请包含：复现步骤、期望行为、实际行为、运行环境

## 协议

本项目采用 [MIT License](LICENSE) 开源协议。
