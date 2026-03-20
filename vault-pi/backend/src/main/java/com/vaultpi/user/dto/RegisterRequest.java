package com.vaultpi.user.dto;

import com.vaultpi.common.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "请填写邮箱")
    @Schema(description = "邮箱", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "请填写用户名")
    @Schema(description = "用户名", example = "user1", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "请填写密码")
    @Schema(description = "密码（需符合密码策略）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "邀请码（可选）")
    private String inviteCode;

    /** 校验密码强度，不符合返回错误信息，符合返回 null */
    public String validatePassword() {
        return PasswordPolicy.validate(password);
    }
}
