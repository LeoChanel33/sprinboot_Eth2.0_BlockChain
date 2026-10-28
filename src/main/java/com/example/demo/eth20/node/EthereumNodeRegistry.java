package com.example.demo.eth20.node;

import java.util.Map;

/** Immutable node selection shared by services; distinct from JPA node registration. */
public class EthereumNodeRegistry {
    private final String defaultNode;
    private final Map<String, EthereumRpcClient> clients;

    public EthereumNodeRegistry(String defaultNode, Map<String, EthereumRpcClient> clients) {
        this.defaultNode = defaultNode;
        this.clients = Map.copyOf(clients);
        if (!this.clients.containsKey(defaultNode)) throw new IllegalArgumentException("Unknown default node");
    }

    public EthereumRpcClient defaultClient() {
        return client(defaultNode);
    }

    public EthereumRpcClient client(String nodeId) {
        EthereumRpcClient client = nodeId == null ? null : clients.get(nodeId);
        if (client == null) throw new IllegalArgumentException("Unknown Ethereum node: " + nodeId);
        return client;
    }

    public Map<String, EthereumRpcClient> clients() {
        return clients;
    }
}
