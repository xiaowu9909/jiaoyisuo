package com.vaultpi.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultAndErrorCodeTest {

    @Test
    void result_ok_hasCode0() {
        Result<String> r = Result.ok("data");
        assertEquals(0, r.getCode());
        assertEquals("SUCCESS", r.getMessage());
        assertEquals("data", r.getData());
    }

    @Test
    void result_fail_errorCode_usesEnum() {
        Result<String> r = Result.fail(ErrorCode.UNAUTHORIZED);
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), r.getCode());
        assertEquals(ErrorCode.UNAUTHORIZED.getMessage(), r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void result_fail_errorCode_withOverride() {
        Result<String> r = Result.fail(ErrorCode.PARAM_INVALID, "自定义消息");
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), r.getCode());
        assertEquals("自定义消息", r.getMessage());
    }

    @Test
    void errorCode_hasExpectedCodes() {
        assertEquals(2001, ErrorCode.BALANCE_INSUFFICIENT.getCode());
        assertEquals(3001, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(5002, ErrorCode.RATE_LIMIT.getCode());
    }
}
