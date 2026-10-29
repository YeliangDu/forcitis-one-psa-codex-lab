## 原话

dev 上 ClamAV 安装来源选 A：官方发布页下载安装包（校验签名与 sha256 后本地安装，不加第三方仓库）

## 落点

one-ops 执行 OPS-1021-OPS-02：装 clamd/freshclam（unix socket、不开 TCP）、登记，用 one-platform-file 样本验加密 7z/PDF 与 P95；生产沿用同一来源
