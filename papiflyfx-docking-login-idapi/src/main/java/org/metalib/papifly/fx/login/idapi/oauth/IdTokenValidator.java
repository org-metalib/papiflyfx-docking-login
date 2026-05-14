package org.metalib.papifly.fx.login.idapi.oauth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class IdTokenValidator {

    private IdTokenValidator() {
    }

    public static List<String> validate(String idToken, String expectedIssuer, String expectedAudience, String expectedNonce) {
        List<String> errors = new ArrayList<>();
        if (idToken == null || idToken.isBlank()) {
            errors.add("ID token is null or blank");
            return errors;
        }

        Map<String, Object> claims = decodePayload(idToken);
        if (claims == null) {
            errors.add("Failed to decode ID token payload");
            return errors;
        }

        String issuer = stringClaim(claims, "iss");
        if (expectedIssuer != null && !expectedIssuer.equals(issuer)) {
            errors.add("Issuer mismatch: expected " + expectedIssuer + ", got " + issuer);
        }

        Object aud = claims.get("aud");
        if (expectedAudience != null) {
            if (aud instanceof String audString) {
                if (!expectedAudience.equals(audString)) {
                    errors.add("Audience mismatch: expected " + expectedAudience + ", got " + audString);
                }
            } else {
                errors.add("Missing or invalid audience claim");
            }
        }

        String nonce = stringClaim(claims, "nonce");
        if (expectedNonce != null && !expectedNonce.equals(nonce)) {
            errors.add("Nonce mismatch");
        }

        Object expObj = claims.get("exp");
        if (expObj instanceof Number expNum) {
            Instant expiry = Instant.ofEpochSecond(expNum.longValue());
            if (Instant.now().isAfter(expiry)) {
                errors.add("Token has expired");
            }
        } else {
            errors.add("Missing exp claim");
        }

        return errors;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            return parseSimpleJson(json);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSimpleJson(String json) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1);
        }
        if (json.endsWith("}")) {
            json = json.substring(0, json.length() - 1);
        }
        for (String pair : splitTopLevel(json)) {
            pair = pair.trim();
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = unquote(pair.substring(0, colon).trim());
            String value = pair.substring(colon + 1).trim();
            map.put(key, parseValue(value));
        }
        return map;
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    parts.add(s.substring(start, i));
                    start = i + 1;
                }
            }
        }
        if (start < s.length()) {
            parts.add(s.substring(start));
        }
        return parts;
    }

    private static Object parseValue(String value) {
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unquote(value);
        }
        if ("true".equals(value)) return Boolean.TRUE;
        if ("false".equals(value)) return Boolean.FALSE;
        if ("null".equals(value)) return null;
        try {
            if (value.contains(".")) return Double.parseDouble(value);
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static String stringClaim(Map<String, Object> claims, String key) {
        Object v = claims.get(key);
        return v instanceof String s ? s : null;
    }
}
