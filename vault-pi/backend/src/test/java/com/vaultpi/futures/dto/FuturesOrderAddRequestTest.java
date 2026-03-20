package com.vaultpi.futures.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FuturesOrderAddRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validateBusiness_rejectsInvalidDirection() {
        FuturesOrderAddRequest req = validRequest();
        req.setDirection("INVALID");
        assertEquals("方向必须为 LONG 或 SHORT", req.validateBusiness());
    }

    @Test
    void validateBusiness_rejectsLimitWithoutPrice() {
        FuturesOrderAddRequest req = validRequest();
        req.setType("LIMIT");
        req.setPrice(null);
        assertEquals("限价单必须填写价格", req.validateBusiness());
    }

    @Test
    void validateBusiness_acceptsValidLimit() {
        FuturesOrderAddRequest req = validRequest();
        req.setType("LIMIT");
        req.setPrice(new BigDecimal("50000"));
        assertNull(req.validateBusiness());
    }

    @Test
    void validateBusiness_acceptsValidMarket() {
        FuturesOrderAddRequest req = validRequest();
        req.setType("MARKET");
        req.setPrice(new BigDecimal("50000"));
        assertNull(req.validateBusiness());
    }

    @Test
    void jakartaValidation_rejectsNullAmount() {
        FuturesOrderAddRequest req = validRequest();
        req.setAmount(null);
        Set<ConstraintViolation<FuturesOrderAddRequest>> v = validator.validate(req);
        assertFalse(v.isEmpty());
    }

    private static FuturesOrderAddRequest validRequest() {
        FuturesOrderAddRequest req = new FuturesOrderAddRequest();
        req.setSymbol("BTC/USDT");
        req.setDirection("LONG");
        req.setType("MARKET");
        req.setAmount(new BigDecimal("0.01"));
        req.setPrice(new BigDecimal("50000"));
        return req;
    }
}
