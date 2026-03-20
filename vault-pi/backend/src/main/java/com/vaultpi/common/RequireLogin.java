package com.vaultpi.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标注后，拦截器会校验 session 中的 memberId 并将之写入 request 的 "memberId" 属性，否则返回 401 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
}
