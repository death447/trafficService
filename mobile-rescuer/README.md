# 救援移动端（uni-app H5）

道路交通事故救援派单系统的移动端（施救员工作台 + 停车场扣车工作台），首期目标为 **H5**。一个应用，按账号权限进入对应工作台。

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

## 演示账号与工作台

登录后按权限进入工作台（无 `rescuer:*`/`mobile:rescuer`、无 `detain:query`、且无 `accident:*`/`accident:manage` 则提示「无移动端权限」）：

| 用户名 | 密码 | 权限 | 登录后 |
|--------|------|------|--------|
| towdriver | admin123 | 仅施救 | 直接进入施救任务工作台 |
| parkingadmin | admin123 | 仅扣车（`detain:query`） | 直接进入停车场在库列表 |
| trafficpolice | admin123 | 仅交警（`accident:*`） | 直接进入交警任务列表 |
| admin | admin123 | 施救+扣车+交警 | 先到「选择工作台」页，点选后进入 |

双权限账号一次只进一套工作台。**换工作台必须退出登录再选**；「我的」不提供切换入口。单权限账号跳过选择页。

登录页提示：`towdriver / parkingadmin / trafficpolice / admin123`。

## 功能页

**施救工作台**（`workspace=rescuer`）

- 任务列表（待办 / 已办 / 中止）与详情（接单、退单、签到、完成）
- 现场采集 / 入库登记（文本 + 拍照上传；不写入扣车表）
- 我的：资料编辑、扫码/手输绑车（`RV:{id}`）；GPS 上报仅此工作台启动

**停车场工作台**（`workspace=parking`）

- 在库列表、入库、扣车详情与出库、扫吊牌（扣押编号原文，如 `DV202609070001`）
- 我的：资料编辑；无绑车、不启动 GPS
- 停车场 CRUD、扣车清理/吊牌打印仍走 PC

**交警工作台**（`workspace=police`）

- 全部任务列表（分页）；已完成未评可评价
- 只读详情（过程时间、现场/入库照片、事故点地图）
- 四维 5 星评价（默认好评，每单一次不可改）
- 我的：资料编辑；无绑车、不启动 GPS

`pages/mine/scan` 是共用摄像头页（绑车与吊牌查找都走它），不按施救工作台拦截。

## 说明

- 使用 `uni.request` / `uni.setStorage` / `uni.getLocation` / `uni.chooseImage` / `uni.uploadFile` / `uni.scanCode`
- H5 下定位与扫码受浏览器权限限制；**仅无法获取定位时**可手动签到（距离超 500m 的自动签到失败不会改手动）；扫码失败可手输 `RV:{id}` 或扣押编号
