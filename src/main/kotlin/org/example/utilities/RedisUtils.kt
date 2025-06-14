package org.example.utilities

import redis.clients.jedis.JedisPool

fun JedisPool.hincrby(key: String, field: String, value: Long): Long {
    return this.resource.use { jedis ->
        jedis.hincrBy(key, field, value)
    }
}

fun JedisPool.hincrbyfloat(key: String, field: String, value: Double): Double {
    return this.resource.use { jedis ->
        jedis.hincrByFloat(key, field, value)
    }
}

fun JedisPool.hgetall(key: String): Map<String, String> {
    return this.resource.use { jedis ->
        jedis.hgetAll(key) ?: emptyMap()
    }
}