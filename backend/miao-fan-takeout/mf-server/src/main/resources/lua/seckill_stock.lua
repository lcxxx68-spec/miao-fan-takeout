-- =============================================================
-- 抢购库存原子扣减脚本
-- 为什么必须用 Lua: Redis 执行脚本时是单线程原子的,
-- "查库存 -> 判断是否售罄 -> 扣减 -> 记录用户" 这几步之间不会被其他请求插入,
-- 从而彻底消除超卖窗口; 用 Java 写这几步就会产生竞态条件
-- =============================================================
-- KEYS[1] = 剩余库存 key      (String)
-- KEYS[2] = 用户已购数量 key  (Hash: userId -> 数量)
-- ARGV[1] = userId
-- ARGV[2] = 每人限购数量
-- ARGV[3] = 活动状态(1=已上架)
-- ARGV[4] = 活动开始时间(毫秒时间戳)
-- ARGV[5] = 活动结束时间(毫秒时间戳)
-- ARGV[6] = 当前时间(毫秒时间戳, 由 Java 传入)
-- 返回: 0成功 1售罄 2超出限购 3未开始 4已结束 5未上架

local stockKey = KEYS[1]
local userCountKey = KEYS[2]

local userId = ARGV[1]
local limit = tonumber(ARGV[2])
local status = tonumber(ARGV[3])
local startTime = tonumber(ARGV[4])
local endTime = tonumber(ARGV[5])
local now = tonumber(ARGV[6])

-- 1. 活动状态与时间校验
if status ~= 1 then
    return 5
end
if now < startTime then
    return 3
end
if now > endTime then
    return 4
end

-- 2. 库存校验: key 不存在时 GET 返回 false, tonumber(false) 为 nil
local stock = tonumber(redis.call('GET', stockKey))
if stock == nil or stock <= 0 then
    return 1
end

-- 3. 限购校验
local bought = tonumber(redis.call('HGET', userCountKey, userId) or '0')
if bought >= limit then
    return 2
end

-- 4. 扣减库存并记录该用户已购数量
redis.call('DECR', stockKey)
redis.call('HINCRBY', userCountKey, userId, 1)

return 0
