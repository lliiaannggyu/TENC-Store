# TENC Store 使用统计（无服务器方案）

统计商店的 **累积用户 / 每日打开次数 / 累计打开次数 / 当前在线人数**。
后端为 Cloudflare Workers + KV（免费额度 10 万请求/天），无需自己的服务器。

## 数据口径

| 指标 | 口径 |
|---|---|
| 累积用户 | 去重设备数（匿名随机 UUID，首次打开后固定） |
| 每日打开次数 | 每次应用进入前台 +1（按东八区日期） |
| 累计打开次数 | 历史所有打开次数之和 |
| 在线人数 | 最近 5 分钟内有前台心跳的设备数 |

隐私：只上报随机 UUID 与事件类型，不采集 IMEI / 安卓 ID / 任何个人数据。

## 部署步骤（约 10 分钟）

1. 注册 [Cloudflare](https://dash.cloudflare.com/) 账号（免费，无需信用卡）
2. 本机安装 Wrangler CLI：`npm install -g wrangler`，然后 `wrangler login`
3. 创建 KV 存储：`npx wrangler kv namespace create STATS_KV`，
   把输出的 id 填进 `wrangler.toml` 的 `kv_namespaces.id`
4. 在 `stats-worker/` 目录执行 `npx wrangler deploy`
5. 部署成功会得到 `https://tenc-store-stats.<你的子域>.workers.dev` 地址。
   **注意：workers.dev 域名在国内无法直连**，需要绑一个自己的域名
   （Cloudflare 控制台 → Workers → 你的 Worker → Settings → Domains & Routes 添加自定义域名）
6. 把最终地址（如 `https://stats.example.com`）写入项目根目录 `secrets.properties`：
   ```properties
   STATS_URL=https://stats.example.com
   ```
7. 重新编译打包即可。地址为空时统计自动关闭，应用不产生任何相关请求。

## 验证

浏览器访问 `https://你的域名/stats`，应返回类似：

```json
{"users":1,"online":1,"todayOpens":3,"totalOpens":3,"date":"2026-11-01"}
```
