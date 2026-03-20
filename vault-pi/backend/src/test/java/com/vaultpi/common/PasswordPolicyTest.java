package com.vaultpi.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void validate_nullOrBlank_returnsError() {
        assertEquals("请填写密码", PasswordPolicy.validate(null));
        assertEquals("请填写密码", PasswordPolicy.validate(""));
        assertEquals("请填写密码", PasswordPolicy.validate("   "));
    }

    @Test
    void validate_tooShort_returnsLengthError() {
        assertNotNull(PasswordPolicy.validate("a1"));
        assertNotNull(PasswordPolicy.validate("abc12")); // 5
        assertTrue(PasswordPolicy.validate("abc1234").contains("至少"));
    }

    @Test
    void validate_tooLong_returnsLengthError() {
        String longPwd = "a".repeat(PasswordPolicy.MAX_LENGTH + 1);
        assertNotNull(PasswordPolicy.validate(longPwd));
    }

    @Test
    void validate_noLetter_returnsComplexityError() {
        assertEquals("密码需同时包含大写字母、小写字母和数字", PasswordPolicy.validate("12345678"));
    }

    @Test
    void validate_noDigit_returnsComplexityError() {
        assertEquals("密码需同时包含大写字母、小写字母和数字", PasswordPolicy.validate("abcdefgh"));
    }

    @Test
    void validate_noUpperOrNoLower_returnsComplexityError() {
        assertNotNull(PasswordPolicy.validate("abc12345"));
        assertNotNull(PasswordPolicy.validate("password1"));
        assertNotNull(PasswordPolicy.validate("ABCD1234"));
    }

    @Test
    void validate_valid_returnsNull() {
        assertNull(PasswordPolicy.validate("Admin123"));
        assertNull(PasswordPolicy.validate("Password1"));
        assertNull(PasswordPolicy.validate("aB3cD4eF"));
    }
}
