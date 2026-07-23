package com.gskart.cart.redis.config;

import com.gskart.cart.redis.entities.Cart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisKeyValueTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.convert.KeyspaceConfiguration;
import org.springframework.data.redis.core.convert.MappingConfiguration;
import org.springframework.data.redis.core.index.IndexConfiguration;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import java.util.Collections;

@Configuration
@EnableRedisRepositories(value = "com.gskart.cart.redis")
public class RedisConfiguration {

    @Value("${gskart.redis.server}")
    private String redisHost;

    @Value("${gskart.redis.port}")
    private int redisPort;

    @Value("${gskart.redis.password}")
    private String redisPassword;

    // Cart cache TTL in minutes (single source of truth: gskart.redis.ttl).
    @Value("${gskart.redis.ttl}")
    private int cacheTTLMinutes;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(redisHost, redisPort);
        redisStandaloneConfiguration.setPassword(redisPassword);
        return new LettuceConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        RedisTemplate<byte[], byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }

    /** String-serialized template used by the transactional outbox (RedisOutboxStore). */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory){
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    public RedisMappingContext redisMappingContext() {
        return new RedisMappingContext(
                new MappingConfiguration(new IndexConfiguration(), new GskartKeySpaceConfiguration(cacheTTLMinutes)));
    }

    /** Applies the configured cart TTL (gskart.redis.ttl, in minutes) to the {@code carts} keyspace. */
    public static class GskartKeySpaceConfiguration extends KeyspaceConfiguration {

        private static final int SECONDS_PER_MINUTE = 60;

        private final long ttlSeconds;

        public GskartKeySpaceConfiguration(int ttlMinutes) {
            this.ttlSeconds = (long) ttlMinutes * SECONDS_PER_MINUTE;
        }

        @Override
        protected Iterable<KeyspaceConfiguration.KeyspaceSettings> initialConfiguration() {
            KeyspaceSettings keyspaceSettings = new KeyspaceSettings(Cart.class, "carts");
            keyspaceSettings.setTimeToLive(ttlSeconds);
            return Collections.singleton(keyspaceSettings);
        }
    }

    @Bean
    public RedisKeyValueTemplate redisKeyValueTemplate(
            RedisMappingContext redisMappingContext,
            RedisTemplate<?, ?> redisTemplate){
        RedisKeyValueAdapter redisKeyValueAdapter = new RedisKeyValueAdapter(redisTemplate);
        return new RedisKeyValueTemplate(redisKeyValueAdapter, redisMappingContext);
    }
}
