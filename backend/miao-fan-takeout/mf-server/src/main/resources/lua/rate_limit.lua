-- =============================================================
-- 令牌桶限流脚本
-- 桶里按时间流逝速度补充令牌, 请求取走令牌才放行, 取不到就限流
-- 与"固定窗口计数"相比, 令牌桶允许突发流量(桶容量), 又限制了长期速率
-- =============================================================
-- KEYS[1] = 限流 key
-- ARGV[1] = 桶容量 capacity
-- ARGV[2] = 每秒补充的令牌数 rate
-- ARGV[3] = 当前时间(毫秒时间戳)
-- ARGV[4] = 本次请求消耗的令牌数
-- 返回: 1放行 0限流

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'lastTime')
local tokens = tonumber(bucket[1])
local lastTime = tonumber(bucket[2])

-- 第一次访问: 桶是满的
if tokens == nil or lastTime == nil then
    tokens = capacity
    lastTime = now
end

-- 按距离上次访问的时间差补充令牌, 最多补满
local delta = math.max(0, now - lastTime) / 1000.0
local filled = math.min(capacity, tokens + delta * rate)

local allowed = 0
if filled >= requested then
    filled = filled - requested
    allowed = 1
end

redis.call('HMSET', key, 'tokens', string.format('%.4f', filled), 'lastTime', now)
-- 桶长时间不用就清理掉, 避免 key 无限堆积
redis.call('PEXPIRE', key, math.ceil(capacity / rate * 1000) + 1000)

return allowed
