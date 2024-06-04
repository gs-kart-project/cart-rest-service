package com.gskart.cart.redis.config;

import com.gskart.cart.redis.entities.Cart;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.RedisKeyValueTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.convert.KeyspaceConfiguration;
import org.springframework.data.redis.core.convert.MappingConfiguration;
import org.springframework.data.redis.core.index.IndexConfiguration;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import java.time.Duration;
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

    @Value("${gskart.redis.ttl}")
    private int cacheTTL;

    private final int MINS = 60;

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

    @Bean
    public RedisMappingContext redisMappingContext() {
//        GskartKeySpaceConfiguration keySpaceConfiguration = new GskartKeySpaceConfiguration();
        return new RedisMappingContext(new MappingConfiguration(new IndexConfiguration(), new GskartKeySpaceConfiguration()));
    }

    public static class GskartKeySpaceConfiguration extends KeyspaceConfiguration {
       /* @Setter
        private int cacheTTL;

        private final int MINS = 60;*/

        @Override
        protected Iterable<KeyspaceConfiguration.KeyspaceSettings> initialConfiguration() {
            KeyspaceSettings keyspaceSettings = new KeyspaceSettings(Cart.class, "carts");
            keyspaceSettings.setTimeToLive((long) (30 * 60));
            return Collections.singleton(keyspaceSettings);
        }
    }

    @Bean
    public RedisKeyValueTemplate redisKeyValueTemplate(
            RedisMappingContext redisMappingContext,
            RedisTemplate<?, ?> redisTemplate){
        RedisKeyValueAdapter redisKeyValueAdapter = new RedisKeyValueAdapter(redisTemplate);
        RedisKeyValueTemplate redisKeyValueTemplate = new RedisKeyValueTemplate(redisKeyValueAdapter, redisMappingContext);

        return redisKeyValueTemplate;
    }

    /*private RedisTemplate<String, Object> stringObjectRedisTemplate(RedisConnectionFactory redisConnectionFactory){
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }*/

    /*
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(this.cacheTTL))
                .disableCachingNullValues();

        RedisCacheManager redisCacheManager = RedisCacheManager
                .builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
        return redisCacheManager;
    }*/


}
