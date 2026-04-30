package com.lawforyou.user.service;

/**
 * Manages the JWT token blacklist backed by Redis.
 *
 * <p>Blacklisted tokens are rejected by {@link com.lawforyou.user.security.TokenBlacklistFilter}
 * before they reach the JWT validation filter.</p>
 */
public interface TokenBlacklistService {

    /**
     * Adds a token to the blacklist with a TTL.
     *
     * @param token     the raw JWT compact string
     * @param ttlMillis time-to-live in milliseconds (should match token expiry)
     */
    void blacklist(String token, long ttlMillis);

    /**
     * Returns {@code true} if the token has been blacklisted (i.e. logged out).
     *
     * @param token the raw JWT compact string
     */
    boolean isBlacklisted(String token);
}

