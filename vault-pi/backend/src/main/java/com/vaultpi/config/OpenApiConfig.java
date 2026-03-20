package com.vaultpi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8081}")
    private int serverPort;

    @Bean
    public OpenAPI vaultPiOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Vault π API")
                .description("Vault π 交易平台后端接口文档")
                .version("1.0"))
            .servers(List.of(
                new Server().url("http://localhost:" + serverPort).description("本地"),
                new Server().url("/").description("当前主机")));
    }
}
