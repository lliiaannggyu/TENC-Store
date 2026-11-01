/**
 * TENC Store 使用统计 Worker（Cloudflare Workers + KV，无自建服务器）。
 *
 * 接口：
 *   GET /ping?id=<设备UUID>&ev=open|beat   上报：open 计打开次数+新用户，beat 为前台心跳
 *   GET /stats                             查询：users 累积用户 / online 在线 / todayOpens / totalOpens
 *
 * KV 绑定名：KV（在 wrangler.toml 中配置）
 * 免费额度 10 万请求/天，个人商店规模完全够用。
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url)

    if (url.pathname === "/ping") {
      return handlePing(url, env)
    }
    if (url.pathname === "/stats") {
      return handleStats(env)
    }
    return new Response("404", { status: 404 })
  },
}

/** 日期按东八区计算，与国内用户的"今天"一致。 */
function cstToday() {
  return new Date(Date.now() + 8 * 3600 * 1000).toISOString().slice(0, 10)
}

async function incr(env, key) {
  const v = Number((await env.KV.get(key)) || 0)
  await env.KV.put(key, String(v + 1))
}

async function handlePing(url, env) {
  // 只保留安全字符，防止恶意长键
  const id = (url.searchParams.get("id") || "").replace(/[^a-f0-9]/gi, "").slice(0, 64)
  const ev = url.searchParams.get("ev") === "open" ? "open" : "beat"
  if (!id) return json({ ok: false })

  if (ev === "open") {
    await incr(env, "opens:total")          // 累计打开次数
    await incr(env, `opens:${cstToday()}`)  // 每日打开次数
    await env.KV.put(`user:${id}`, "1")     // 累积用户（去重设备，永久留存）
  }
  // 在线标记：5 分钟内有心跳视为在线（TTL 1 天自动清理）
  await env.KV.put(`live:${id}`, String(Date.now()), { expirationTtl: 86400 })
  return json({ ok: true })
}

async function handleStats(env) {
  const now = Date.now()
  const today = cstToday()

  // 累积用户：user: 前缀键总数（分页统计）
  let users = 0
  let cursor
  do {
    const page = await env.KV.list({ prefix: "user:", cursor })
    users += page.keys.length
    cursor = page.list_complete ? undefined : page.cursor
  } while (cursor)

  // 在线：live: 键中时间戳在 5 分钟内的数量（只统计前 1000 个，规模足够）
  let online = 0
  const livePage = await env.KV.list({ prefix: "live:" })
  for (const k of livePage.keys) {
    const t = Number((await env.KV.get(k.name)) || 0)
    if (now - t < 5 * 60 * 1000) online++
  }

  const totalOpens = Number((await env.KV.get("opens:total")) || 0)
  const todayOpens = Number((await env.KV.get(`opens:${today}`)) || 0)

  return json({ users, online, todayOpens, totalOpens, date: today })
}

function json(obj) {
  return new Response(JSON.stringify(obj), {
    headers: { "Content-Type": "application/json" },
  })
}
