package com.zdph.dksdk.config;

import com.aliyun.dataworks_20240902.Client;
import com.aliyun.teaopenapi.models.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataWorksClientConfig {

    private final DataWorksProperties properties;

    public DataWorksClientConfig(DataWorksProperties properties) {
        this.properties = properties;
    }
    @Bean
    public Client createClient() throws Exception {
        return new Client(new Config()
                .setEndpoint(properties.getEndpoint())
                .setRegionId(properties.getRegionId())
                .setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret()));
    }
}
