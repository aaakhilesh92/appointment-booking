package com.org.booking.app;

import com.org.booking.config.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient embassyRestClient() {
        return RestClient.builder()
                .defaultHeader(Constants.USER_AGENT, Constants.USER_AGENT_VALUE)
                .defaultHeader(Constants.X_REQUESTED_WITH, Constants.X_REQUESTED_WITH)
                .build();
    }
}
