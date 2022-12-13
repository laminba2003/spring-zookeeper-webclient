package com.spring.training.config;

import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.zookeeper.discovery.watcher.DependencyState;
import org.springframework.cloud.zookeeper.discovery.watcher.DependencyWatcherListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;

@Configuration
@Slf4j
public class ApplicationConfig {

    @Bean
    @Profile("!ssl")
    public WebClient webClient(ClientConfig config) {
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)));

        ClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        return webClientBuilder()
                .baseUrl(config.getUrl())
                .clientConnector(connector)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Profile("ssl")
    public WebClient webSSLClient(ClientConfig config) {
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(10))
                        .addHandlerLast(new WriteTimeoutHandler(10)))
                .secure(spec -> {
                    try {
                        String type = config.getSsl().get("key-store-type");
                        KeyStore keyStore = KeyStore.getInstance(type);
                        DefaultResourceLoader loader = new DefaultResourceLoader();
                        String file = config.getSsl().get("key-store");
                        String password = config.getSsl().get("key-store-password");
                        keyStore.load(loader.getResource(file).getInputStream(), password.toCharArray());
                        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                        keyManagerFactory.init(keyStore, password.toCharArray());
                        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        trustManagerFactory.init(keyStore);
                        spec.sslContext(SslContextBuilder.forClient()
                                .keyManager(keyManagerFactory)
                                .trustManager(trustManagerFactory)
                                .build());
                    } catch (Exception e) {
                        log.error("Unable to set SSL Context", e);
                    }
                });

        ClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        return webClientBuilder()
                .baseUrl(config.getUrl())
                .clientConnector(connector)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebProperties.Resources resources() {
        return new WebProperties.Resources();
    }

    @Bean
    @ConfigurationProperties(prefix = "remote.services")
    public ClientConfig clientConfig() {
        return new ClientConfig();
    }

    @Bean
    DependencyWatcherListener dependencyWatcherListener() {
        return (String dependencyName, DependencyState newState) -> log.info("the current state of {} is {}", dependencyName, newState.name());
    }
}
