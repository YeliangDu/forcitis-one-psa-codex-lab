## 原话

H5 用 /h5/ 子路径

## 落点

frontend/apps/h5 publicPath 与路由 basename 为 /h5/（构建期配置，缺失则构建失败）；部署 Nginx 按 /h5/ 子路径；修复批 C 实现（CROSSCHECK N-1）
