package com.jojjer.companiondevicepairing;

public class UuidUtils {
    public static String normalizeUuid(String uuid) {
        if (uuid == null) return null;

        String normalized = uuid.trim()
            .toLowerCase()
            .replaceAll("[\\u2010-\\u2015\\u2212\\uFE58\\uFE63\\uFF0D\\-]", "-")
            .replaceAll("\\s+", "");

        return normalized;
    }
}
