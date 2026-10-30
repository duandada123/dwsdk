package com.zdph.dksdk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "dataworks")
public class DataWorksProperties {
    private String endpoint;
    private String regionId;
    private Long projectId;
    private String accessKeyId;
    private String accessKeySecret;
    private String bizdateZone;
    private int callTimeoutSeconds;
    private int maxNodePages;
    private String projectEnv = "PROD";
}
