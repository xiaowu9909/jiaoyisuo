package com.vaultpi.controller;

import com.vaultpi.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public HealthController(DataSource dataSource, @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        Map<String, String> components = new HashMap<>();
        String dbStatus = checkDb() ? "UP" : "DOWN";
        String redisStatus = checkRedis() ? "UP" : "DOWN";
        components.put("db", dbStatus);
        components.put("redis", redisStatus);
        String overall = (dbStatus.equals("UP") && redisStatus.equals("UP")) ? "UP" : "DOWN";
        Map<String, Object> data = new HashMap<>();
        data.put("status", overall);
        data.put("components", components);
        data.put("app", "vault-pi");
        return Result.ok(data);
    }

    private boolean checkDb() {
        try (Connection c = dataSource.getConnection()) {
            return c.createStatement().executeQuery("SELECT 1").next();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedis() {
        if (redisTemplate == null) return false;
        try {
            return "PONG".equals(redisTemplate.getConnectionFactory().getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }
}
