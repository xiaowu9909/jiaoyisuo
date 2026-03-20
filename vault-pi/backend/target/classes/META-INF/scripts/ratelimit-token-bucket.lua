-- 令牌桶：KEYS[1]=key, ARGV[1]=now_sec, ARGV[2]=capacity, ARGV[3]=refill_per_minute
local tokens = tonumber(redis.call("HGET", KEYS[1], "tokens") or "0")
local last = tonumber(redis.call("HGET", KEYS[1], "last") or "0")
local now = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])
if last == 0 then
  tokens = capacity
  last = now
else
  local elapsed_min = (now - last) / 60.0
  tokens = math.min(capacity, tokens + elapsed_min * rate)
  last = now
end
if tokens >= 1 then
  tokens = tokens - 1
  redis.call("HSET", KEYS[1], "tokens", tokens, "last", last)
  redis.call("PEXPIRE", KEYS[1], 120000)
  return 1
else
  return 0
end
