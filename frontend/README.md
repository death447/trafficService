# Frontend - Vue3 + Vite

## 项目简介

基于 Vue3 + Vite + Vue Router + Pinia + Axios 的前端项目框架。

## 技术栈

- Vue 3.x
- Vite 5.x
- Vue Router 4.x
- Pinia 2.x
- Axios 1.x

## 目录结构

```
frontend/
├── public/                 # 静态资源
├── src/
│   ├── assets/            # 资源文件
│   ├── components/        # 公共组件
│   ├── router/            # 路由配置
│   ├── views/             # 页面组件
│   ├── stores/            # Pinia状态管理
│   ├── utils/             # 工具函数
│   ├── App.vue            # 根组件
│   └── main.js            # 入口文件
├── .env.development       # 开发环境变量
├── index.html             # HTML模板
├── vite.config.js         # Vite配置
└── package.json           # 项目依赖
```

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发环境运行

```bash
npm run dev
```

访问地址: http://localhost:5173

### 生产环境构建

```bash
npm run build
```

### 预览生产构建

```bash
npm run preview
```

## 配置说明

### API代理配置

开发环境下，前端会自动将 `/api` 开头的请求代理到后端服务 (http://localhost:8080)。

配置文件: `vite.config.js`

### 环境变量

在 `.env.development` 文件中配置环境变量。

## 开发建议

1. 使用 Vue 3 Composition API (`<script setup>`) 编写组件
2. 使用 Pinia 进行状态管理
3. 遵循 ESLint 和 Prettier 规范（需要自行配置）
4. 组件和页面文件使用 PascalCase 命名

## 常用命令

- `npm run dev` - 启动开发服务器
- `npm run build` - 构建生产版本
- `npm run preview` - 预览生产构建