package com.example.demo.eth20;

import com.example.demo.eth20.entity.BlockchainNode;
import com.example.demo.eth20.repository.*;
import com.example.demo.eth20.service.api.ClientApiService;
import com.example.demo.eth20.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ClientApiServiceIntegrationTests {
    @Autowired ClientApiService clientService;
    @Autowired UserAccountRepository users;
    @Autowired BlockchainNodeRepository nodes;
    @Autowired PlatformAccountRepository platforms;

    @BeforeEach
    void clearDatabase() {
        platforms.deleteAll();
        nodes.deleteAll();
        users.deleteAll();
    }

    @Test
    void registersAndLinksPlatformUsingInjectedServices() {
        var user = clientService.registerUser(new Registration(" Leo ", "Leo@example.com",
                new PlatformLink(" Web ", "external-1")));
        assertEquals("leo", clientService.getUser(user.id()).username());
        assertEquals("leo@example.com", user.email());
        var links = clientService.getPlatforms(user.id());
        assertEquals(1, links.size());
        assertEquals("web", links.get(0).platform());
        assertEquals(user.id(), links.get(0).userId());
    }

    @Test
    void rollsBackNewUserWhenPlatformLinkFails() {
        clientService.registerUser(new Registration("first", "first@example.com",
                new PlatformLink("web", "shared-account")));
        var error = assertThrows(ResponseStatusException.class, () -> clientService.registerUser(
                new Registration("second", "second@example.com",
                        new PlatformLink("web", "shared-account"))));
        assertEquals(409, error.getStatusCode().value());
        assertFalse(users.existsByUsername("second"));
        assertEquals(1, users.count());
        assertEquals(1, platforms.count());
    }

    @Test
    void rejectsDuplicateUserAndMissingPlatformOwner() {
        clientService.registerUser(new Registration("Leo", "leo@example.com", null));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> clientService.registerUser(
                new Registration("LEO", "other@example.com", null))).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () ->
                clientService.linkPlatform(Long.MAX_VALUE, new PlatformLink("web", "1")))
                .getStatusCode().value());
    }

    @Test
    void persistsNodeStatusAndRejectsInvalidNodes() {
        clientService.registerNode(new NodeRegistration("node-1", "http://localhost:8545", 11155111L));
        clientService.updateNodeStatus("node-1", new NodeStatusUpdate(BlockchainNode.Status.SYNCING));
        assertEquals(BlockchainNode.Status.SYNCING, clientService.getRegisteredNode("node-1").getStatus());
        assertThrows(IllegalArgumentException.class, () -> clientService.registerNode(
                new NodeRegistration("bad", "file:///tmp/node", 1L)));
        assertEquals(1, nodes.count());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> clientService.getRegisteredNode("missing"))
                .getStatusCode().value());
    }
}
