package com.vaultpi.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void valid_whenUsernameAndPasswordPresent() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("pass1234");
        assertTrue(validator.validate(req).isEmpty());
    }

    @Test
    void invalid_whenUsernameBlank() {
        LoginRequest req = new LoginRequest();
        req.setUsername("  ");
        req.setPassword("pass1234");
        Set<ConstraintViolation<LoginRequest>> v = validator.validate(req);
        assertFalse(v.isEmpty());
        assertTrue(v.stream().anyMatch(c -> c.getMessage().contains("用户名")));
    }

    @Test
    void invalid_whenPasswordBlank() {
        LoginRequest req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("");
        Set<ConstraintViolation<LoginRequest>> v = validator.validate(req);
        assertFalse(v.isEmpty());
        assertTrue(v.stream().anyMatch(c -> c.getMessage().contains("密码")));
    }
}
