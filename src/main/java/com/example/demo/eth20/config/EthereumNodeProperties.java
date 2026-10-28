package com.example.demo.eth20.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application connection settings, independent of Docker container lifecycle. */
@ConfigurationProperties(prefix = "ethereum")
public record EthereumNodeProperties(String defaultNode, Duration connectTimeout,
        Duration requestTimeout, boolean checkOnStartup, boolean failFast, Map<String, Node> nodes) {
    public EthereumNodeProperties {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                || requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Ethereum timeouts must be positive");
        }
        if (nodes == null || nodes.isEmpty() || defaultNode == null || !nodes.containsKey(defaultNode)) {
            throw new IllegalArgumentException("ethereum.default-node must identify a configured node");
        }
        nodes = Map.copyOf(nodes);
    }

    public record Node(URI rpcUrl, URI beaconRestUrl, long chainId, boolean validator) {
        public Node {
            requireHttp(rpcUrl);
            requireHttp(beaconRestUrl);
            if (chainId <= 0) throw new IllegalArgumentException("Ethereum chain ID must be positive");
        }

        private static void requireHttp(URI uri) {
            if (uri == null || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Ethereum endpoints must be HTTP(S) URLs without credentials or fragments");
            }
        }
    }
}
