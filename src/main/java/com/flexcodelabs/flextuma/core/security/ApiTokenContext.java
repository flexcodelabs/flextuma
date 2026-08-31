package com.flexcodelabs.flextuma.core.security;
import java.util.Set;
import java.util.UUID;
public final class ApiTokenContext {
    public static final String SEND_MESSAGES = "MESSAGES_SEND";
    private static final ThreadLocal<TokenGrant> CURRENT = new ThreadLocal<>();
    private ApiTokenContext() { }
    public static void set(TokenGrant grant) { CURRENT.set(grant); }
    public static TokenGrant get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
    public record TokenGrant(Set<String> scopes, Set<UUID> connectorIds, boolean allowSystemConnectors) {
        public boolean allows(String scope) { return scopes != null && scopes.contains(scope); }
        public boolean allowsConnector(UUID connectorId) { return connectorIds != null && connectorIds.contains(connectorId); }
    }
}
