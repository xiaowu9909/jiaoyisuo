package com.vaultpi.exchange.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrderAddRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validateBusiness_rejectsInvalidDirection() {
        OrderAddRequest req = validRequest();
        req.setDirection("INVALID");
        assertEquals("方向必须为 BUY 或 SELL", req.validateBusiness());
    }

    @Test
    void validateBusiness_rejectsLimitWithoutPrice() {
        OrderAddRequest req = validRequest();
        req.setType("LIMIT");
        req.setPrice(null);
        assertEquals("限价单必须填写价格且大于 0", req.validateBusiness());
    }

    @Test
    void validateBusiness_acceptsValidLimit() {
        OrderAddRequest req = validRequest();
        req.setType("LIMIT");
        req.setPrice(new BigDecimal("50000"));
        assertNull(req.validateBusiness());
    }

    @Test
    void validateBusiness_acceptsValidMarket() {
        OrderAddRequest req = validRequest();
        req.setType("MARKET");
        req.setPrice(null);
        assertNull(req.validateBusiness());
    }

    @Test
    void jakartaValidation_rejectsNullAmount() {
        OrderAddRequest req = validRequest();
        req.setAmount(null);
        Set<ConstraintViolation<OrderAddRequest>> v = validator.validate(req);
        assertFalse(v.isEmpty());
    }

    @Test
    void jakartaValidation_rejectsZeroAmount() {
        OrderAddRequest req = validRequest();
        req.setAmount(BigDecimal.ZERO);
        Set<ConstraintViolation<OrderAddRequest>> v = validator.validate(req);
        assertFalse(v.isEmpty());
    }

    private static OrderAddRequest validRequest() {
        OrderAddRequest req = new OrderAddRequest();
        req.setSymbol("BTC/USDT");
        req.setDirection("BUY");
        req.setType("MARKET");
        req.setAmount(new BigDecimal("0.001"));
        return req;
    }
}
