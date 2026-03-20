package com.vaultpi.system.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "system_config")
@Data
public class SystemConfig {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id; // Parameter key, e.g., "MEMBER_REGISTER_OPEN"

    /** 列名 config_value 避免 H2 保留字 value 导致 DDL 报错；已有 MySQL 库需执行 ALTER TABLE system_config CHANGE COLUMN `value` config_value TEXT NOT NULL; */
    @Column(name = "config_value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "remark")
    private String remark;

    @Column(name = "group_name")
    private String groupName; // Group for UI categorization
}
