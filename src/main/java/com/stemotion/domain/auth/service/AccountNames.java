package com.stemotion.domain.auth.service;

import com.stemotion.core.exception.ApiException;
import java.util.Locale;

/** The same canonical identifier is used for provisioning and password login. */
public final class AccountNames {
    private AccountNames() {}
    public static String fullName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.length() < 2 || name.length() > 100 || name.chars().anyMatch(Character::isISOControl))
            throw ApiException.badRequest("Tên phải có 2–100 ký tự và không có ký tự điều khiển.");
        return name;
    }
    public static String username(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("^[a-z0-9][a-z0-9._-]{2,31}$"))
            throw ApiException.badRequest("Tài khoản phải có 3–32 ký tự a-z, 0-9, dấu chấm, gạch dưới hoặc gạch ngang; không chứa @.");
        return value;
    }
}
