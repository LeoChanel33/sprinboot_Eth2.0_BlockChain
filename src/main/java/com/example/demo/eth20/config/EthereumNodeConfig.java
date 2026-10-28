package com.example.demo.eth20.config;

import com.example.demo.eth20.node.EthereumNodeRegistry;
import com.example.demo.eth20.node.EthereumRpcClient;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EthereumNodeProperties.class)
public class EthereumNodeConfig {
    @Bean
    public EthereumNodeRegistry ethereumNodeRegistry(EthereumNodeProperties properties) {
        HttpClient transport = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(properties.requestTimeout());
        RestClient http = RestClient.builder().requestFactory(factory).build();
        Map<String, EthereumRpcClient> clients = new LinkedHashMap<>();
        properties.nodes().forEach((id, settings) -> clients.put(id, new EthereumRpcClient(id, settings, http)));
        return new EthereumNodeRegistry(properties.defaultNode(), clients);
    }

    @Bean
    public ApplicationRunner ethereumNodeInitializer(EthereumNodeProperties properties, EthereumNodeRegistry registry) {
        return args -> {
            log.info("Loaded {} Ethereum nodes; default node: {}", registry.clients().size(), properties.defaultNode());
            if (!properties.checkOnStartup()) return;
            registry.clients().forEach((id, client) -> {
                try {
                    client.verifyChainId();
                    log.info("Ethereum node {} chain ID verified: {}", id, client.settings().chainId());
                } catch (RuntimeException ex) {
                    if (properties.failFast()) throw new IllegalStateException("Ethereum startup check failed: " + id, ex);
                    log.warn("Ethereum node {} startup check failed ({}); retry on next service call", id,
                            ex.getClass().getSimpleName());
                }
            });
        };
    }
}
