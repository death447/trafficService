# 首页与新建工单地图：启用片区叠加

## 决策
- 首页概览地图、新建工单选点地图叠加 **ENABLED** 片区围栏（样式与片区编辑参考层一致）
- 片区 **不参与** setFitView；首页仍按事故/车辆，新建仍跟事故点
- 点击穿透，不挡选点

## 实现
- `amap.js`：`drawDistrictOverlays` / `clearDistrictOverlays`；片区编辑复用
- `districtOverlay.js`：`loadEnabledDistrictOverlayItems`
- `Home.vue`、`DispatchCreate.vue` 初始化后绘制
