package com.vaultpi.common;

/**
 * 密码策略：最小长度 8 位，需包含大小写字母与数字，避免纯弱口令；与用户名/邮箱相同则拒绝。
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private static final String LENGTH_MSG = "密码长度至少 " + MIN_LENGTH + " 位，且不超过 " + MAX_LENGTH + " 位";
    private static final String COMPLEXITY_MSG = "密码需同时包含大写字母、小写字母和数字";

    /** 校验登录/注册等场景的密码强度，不通过时返回错误信息，通过返回 null */
    public static String validate(String password) {
        if (password == null || password.isBlank()) {
            return "请填写密码";
        }
        if (password.length() < MIN_LENGTH) {
            return LENGTH_MSG;
        }
        if (password.length() > MAX_LENGTH) {
            return LENGTH_MSG;
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            return COMPLEXITY_MSG;
        }
        return null;
    }

    /** 注册时额外校验：密码不得与用户名或邮箱相同（忽略大小写） */
    public static String validateNotSameAsIdentity(String password, String username, String email) {
        String err = validate(password);
        if (err != null) return err;
        if (password == null) return null;
        String p = password.trim();
        if (username != null && !username.isBlank() && p.equalsIgnoreCase(username.trim())) {
            return "密码不能与用户名相同";
        }
        if (email != null && !email.isBlank() && p.equalsIgnoreCase(email.trim())) {
            return "密码不能与邮箱相同";
        }
        return null;
    }
}
