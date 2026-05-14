package org.metalib.papifly.fx.login.idapi;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserPrincipalTest {

    @Test
    void rawClaimsMayContainNullValues() {
        Map<String, Object> rawClaims = new LinkedHashMap<>();
        rawClaims.put("login", "octocat");
        rawClaims.put("name", null);
        rawClaims.put("email", null);

        UserPrincipal principal = new UserPrincipal("123", "octocat", null, null, rawClaims);

        assertEquals("octocat", principal.rawClaims().get("login"));
        assertNull(principal.rawClaims().get("name"));
        assertNull(principal.rawClaims().get("email"));
    }

    @Test
    void rawClaimsRemainImmutable() {
        UserPrincipal principal = new UserPrincipal("123", "octocat", null, null, Map.of("login", "octocat"));

        assertThrows(UnsupportedOperationException.class, () -> principal.rawClaims().put("id", 123L));
    }
}
