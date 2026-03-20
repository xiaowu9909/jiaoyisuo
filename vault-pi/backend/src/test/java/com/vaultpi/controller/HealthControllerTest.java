package com.vaultpi.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试：/api/health 返回结构且 DB/Redis 可连通时 status 为 UP。
 * 需本地 MySQL、Redis 可用；dev 使用 H2 时存在保留字等问题，默认禁用。
 * 使用真实 MySQL 时可用：mvn test -Dtest=HealthControllerTest -Dspring.profiles.active=prod
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Disabled("Full context + H2 与 SystemConfig.value 保留字冲突；生产用 MySQL 时可单独运行")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returnsOkAndStructure() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").exists())
            .andExpect(jsonPath("$.data.components.db").exists())
            .andExpect(jsonPath("$.data.components.redis").exists())
            .andExpect(jsonPath("$.data.app").value("vault-pi"));
    }
}
