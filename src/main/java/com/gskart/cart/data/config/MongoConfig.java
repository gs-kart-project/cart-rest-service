package com.gskart.cart.data.config;

import com.gskart.cart.data.converters.OffsetDateTimeReadConverter;
import com.gskart.cart.data.converters.OffsetDateTimeWriteConverter;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoClientFactoryBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.Arrays;

@Configuration
public class MongoConfig {
    @Value("${gskart.mongo.username}")
    private String userName;

    @Value("${gskart.mongo.password}")
    private String password;

    @Value("${gskart.mongo.databaseName}")
    private String databaseName;

    @Value("${gskart.mongo.host}")
    private String host;

    @Value("${gskart.mongo.port}")
    private Integer port;

    @Bean
    public MongoClientFactoryBean mongoClientFactoryBean() {
        MongoCredential mongoCredential =
            MongoCredential.createCredential(
                userName,
                databaseName,
                password.toCharArray()
            );
        MongoClientFactoryBean mongoClientFactoryBean = new MongoClientFactoryBean();
        mongoClientFactoryBean.setHost(host);
        mongoClientFactoryBean.setPort(port);
        mongoClientFactoryBean.setCredential(new MongoCredential[]{mongoCredential});
        return mongoClientFactoryBean;
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient){
        return new SimpleMongoClientDatabaseFactory(mongoClient, databaseName);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory, MappingMongoConverter mappingMongoConverter){
        return new MongoTemplate(mongoDatabaseFactory, mappingMongoConverter);
    }

    public MappingMongoConverter mappingMongoConverter(
            MongoDatabaseFactory mongoDatabaseFactory,
            MongoCustomConversions mongoCustomConversions,
            MongoMappingContext mongoMappingContext){
        MappingMongoConverter mappingMongoConverter =
                new MappingMongoConverter(new DefaultDbRefResolver(mongoDatabaseFactory), mongoMappingContext);
        mappingMongoConverter.setCustomConversions(mongoCustomConversions);
        return mappingMongoConverter;
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions(){
        return new MongoCustomConversions(Arrays.asList(
                new OffsetDateTimeReadConverter(),
                new OffsetDateTimeWriteConverter()
        ));
    }
}
