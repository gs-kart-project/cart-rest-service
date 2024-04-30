package com.gskart.cart.data.config;

import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoClientFactoryBean;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfig {
    @Value("{gskart.mongo.username}")
    private String userName;

    @Value("{gskart.mongo.password}")
    private String password;

    @Value("{gskart.mongo.databaseName}")
    private String databaseName;

    @Value("{gskart.mongo.host}")
    private String host;

    @Value("{gskart.mongo.port}")
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

    public MongoTemplate mongoTemplate(MongoClient mongoClient){
        return new MongoTemplate(mongoClient, databaseName);
    }
}
