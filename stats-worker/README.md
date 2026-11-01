# TENC Store 使用统计（无服务器方案）

统计商店的 **累积用户 / 每日打开次数 / 累计打开次数 / 当前在线人数**。

## 方案一（推荐，无需域名）：LeanCloud 免费开发版

国内直连稳定，不需要自己的域名和服务器，应用已内置 REST 对接，无需额外 SDK。

1. 注册 [LeanCloud](https://leancloud.cn/) 并完成实名认证
2. 创建应用（选「开发版」，免费），进入 控制台 → 设置 → 应用凭证
3. 记下三项：**API 服务器地址**（形如 `https://xxxx.lc-cn-n1-shared.com`）、**AppID**、**AppKey**
4. 写入项目根目录 `secrets.properties`（该文件不入库）：
   ```properties
   LEANCLOUD_BASE=https://xxxx.lc-cn-n1-shared.com
   LEANCLOUD_ID=你的AppID
   LEANCLOUD_KEY=你的AppKey
   ```
5. 重新编译打包即可。数据在控制台 → 数据存储里可见：
   - `StoreDevice` 表：每台设备一条（uuid / lastActive）= 累积用户
   - `StoreOpenStat` 表：`opens_total`（累计打开）与 `opens_日期`（每日打开）
6. 首次运行会自动建表，无需手动创建 Class。

免费开发版限额约每天 3 万次 API 请求：应用端心跳已按 5 分钟一次优化，
约可支撑上百日活用户；超出后可升级为商用版或在设置里延长心跳间隔。

## 方案二（备选，需自有域名）：Cloudflare Workers + KV

| 指标 | 口径 |
|---|---|
| 累积用户 | 去重设备数（匿名随机 UUID，首次打开后固定） |
| 每日打开次数 | 每次应用进入前台 +1（按东八区日期） |
| 累计打开次数 | 历史所有打开次数之和 |
| 在线人数 | 最近有前台心跳的设备数 |

隐私：只上报随机 UUID 与事件类型，不采集 IMEI / 安卓 ID / 任何个人数据。

### 部署步骤

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

### 验证

浏览器访问 `https://你的域名/stats`，应返回类似：

```json
{"users":1,"online":1,"todayOpens":3,"totalOpens":3,"date":"2026-11-01"}
```

