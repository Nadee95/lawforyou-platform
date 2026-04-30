package com.lawforyou.user.service.impl;

import com.lawforyou.user.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed token blacklist.
 *
 * <p>Key format: {@code token:bl:<jwt>} — value is {@code "1"}, TTL matches configured expiration.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "token:bl:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String token, long ttlMillis) {
        String key = KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
        log.debug("Token blacklisted, TTL={}ms", ttlMillis);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}

