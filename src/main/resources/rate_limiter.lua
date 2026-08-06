local current
current = redis.call('incr',KEYS[1])
if tonumber(current) == 1 then
    redis.call('expire',KEYS[1], ARGV[1])
end
if tonumber(current) > tonumber(ARGV[2]) then
    return 0
end
return 1