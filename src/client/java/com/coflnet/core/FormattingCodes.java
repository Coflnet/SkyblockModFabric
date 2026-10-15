package com.coflnet.core;

import java.util.regex.Pattern;

final class FormattingCodes {
    private static final Pattern VALID_CODE = Pattern.compile("(?i)\u00a7[0-9A-FK-OR]");

    private FormattingCodes() {
    }

    static String strip(String value) {
        return value == null ? null : VALID_CODE.matcher(value).replaceAll("");
    }
}
