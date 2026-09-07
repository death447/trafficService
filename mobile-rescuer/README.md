# 施救员移动端（uni-app H5）

道路交通事故救援派单系统的施救员端，首期目标为 **H5**。

## 环境要求

- Node.js 18+（推荐）
- 后端已启动：默认 `http://localhost:8080`
- 后端 CORS 已对移动端开放

## 安装与启动

```bash
cd mobile-rescuer
npm install
npm run dev:h5
```

浏览器打开终端提示的本地地址（默认约 `http://localhost:5174`）。

生产构建：

```bash
npm run build:h5
```

产物在 `dist/build/h5`。

## API 配置

通过环境变量 `VITE_API_BASE` 配置后端 API 根路径（需包含 `/api`）：

```bash
# .env 或 .env.local
VITE_API_BASE=http://localhost:8080/api
```

未配置时默认 `http://localhost:8080/api`。  
静态上传资源按 `{origin}/uploads/{filePath}` 拼接（自动去掉 `/api` 后缀）。

可参考 `.env.example`。

## 演示账号

| 用户名 | 密码 | 说明 |
|--------|------|------|
| towdriver | admin123 | 施救员（`TOW_DRIVER`）种子用户 |

登录后须具备任一 `rescuer:*` 或 `mobile:rescuer` 权限，否则拒绝进入。

## 功能页

- 登录
- 任务列表（待办 / 已办 / 中止）与详情（接单、退单、签到、完成）
- 现场采集 / 入库登记（文本 + 拍照上传）
- 我的：资料编辑、扫码/手输绑车（`RV:{id}`）

## 说明

- 使用 `uni.request` / `uni.setStorage` / `uni.getLocation` / `uni.chooseImage` / `uni.uploadFile` / `uni.scanCode`
- H5 下定位与扫码受浏览器权限限制；签到失败可改手动签到，扫码失败可手输载荷
