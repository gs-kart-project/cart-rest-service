package com.gskart.cart.integration;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

/**
 * Shared Testcontainers harness (Mongo + Redis + Kafka) for integration tests. The app reads
 * custom {@code gskart.redis.*} / {@code gskart.mongo.*} properties via its own connection
 * factories (RedisConfiguration/MongoConfig), not {@code spring.data.*}, so Spring Boot's
 * {@code @ServiceConnection} auto-wiring does not apply here — properties are mapped explicitly
 * via {@code @DynamicPropertySource} instead. Mongo is provisioned with the same
 * app-user-on-top-of-root-auth model as local dev (docker-compose.yml / docker/mongo/init),
 * reusing that init script verbatim as a test resource.
 */
@Testcontainers
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String MONGO_DATABASE = "gskart-CartDb-it";
    protected static final String MONGO_USER = "cart-it-user";
    protected static final String MONGO_PASSWORD = "cart-it-password";
    protected static final String REDIS_PASSWORD = "cart-it-redis-password";

    @Container
    static GenericContainer<?> mongo = new GenericContainer<>(DockerImageName.parse("mongo:7.0"))
            .withExposedPorts(27017)
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "root")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "root-it-password")
            .withEnv("CART_MONGO_DATABASE", MONGO_DATABASE)
            .withEnv("CART_MONGO_USER", MONGO_USER)
            .withEnv("CART_MONGO_PASSWORD", MONGO_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("mongo-init/01-init-cartdb.js"),
                    "/docker-entrypoint-initdb.d/01-init-cartdb.js")
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 2)
                    .withStartupTimeout(Duration.ofSeconds(60)));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.0"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .waitingFor(Wait.forListeningPort());

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("gskart.mongo.host", mongo::getHost);
        registry.add("gskart.mongo.port", () -> mongo.getMappedPort(27017));
        registry.add("gskart.mongo.databaseName", () -> MONGO_DATABASE);
        registry.add("gskart.mongo.username", () -> MONGO_USER);
        registry.add("gskart.mongo.password", () -> MONGO_PASSWORD);

        registry.add("gskart.redis.server", redis::getHost);
        registry.add("gskart.redis.port", () -> redis.getMappedPort(6379));
        registry.add("gskart.redis.password", () -> REDIS_PASSWORD);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
