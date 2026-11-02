package com.stemotion.domain.auth.service;
import com.stemotion.core.exception.ApiException;
import java.nio.charset.StandardCharsets;

/** BCrypt has a byte limit, which is different from a Java/HTML character count. */
public final class PasswordRules {
    private PasswordRules() {}
    public static boolean canCompare(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
    public static void requireNew(String password) {
        if (password == null || password.length() < 8 || !canCompare(password))
            throw ApiException.badRequest("Mật khẩu phải có ít nhất 8 ký tự và tối đa 72 byte UTF-8.");
    }
}
