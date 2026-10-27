package com.example.demo.eth20;

import com.example.demo.eth20.entity.BlockchainNode;
import com.example.demo.eth20.repository.*;
import com.example.demo.eth20.service.Sevices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class SevicesIntegrationTests {
    @Autowired Sevices sevices;
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
        var user = sevices.register(new Sevices.Registration(" Leo ", "Leo@example.com",
                new Sevices.PlatformLink(" Web ", "external-1")));
        assertEquals("leo", sevices.getUser(user.id()).username());
        assertEquals("leo@example.com", user.email());
        var links = sevices.getPlatforms(user.id());
        assertEquals(1, links.size());
        assertEquals("web", links.get(0).platform());
        assertEquals(user.id(), links.get(0).userId());
    }

    @Test
    void rollsBackNewUserWhenPlatformLinkFails() {
        sevices.register(new Sevices.Registration("first", "first@example.com",
                new Sevices.PlatformLink("web", "shared-account")));
        var error = assertThrows(ResponseStatusException.class, () -> sevices.register(
                new Sevices.Registration("second", "second@example.com",
                        new Sevices.PlatformLink("web", "shared-account"))));
        assertEquals(409, error.getStatusCode().value());
        assertFalse(users.existsByUsername("second"));
        assertEquals(1, users.count());
        assertEquals(1, platforms.count());
    }

    @Test
    void rejectsDuplicateUserAndMissingPlatformOwner() {
        sevices.register(new Sevices.Registration("Leo", "leo@example.com", null));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> sevices.register(
                new Sevices.Registration("LEO", "other@example.com", null))).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () ->
                sevices.linkPlatform(Long.MAX_VALUE, new Sevices.PlatformLink("web", "1")))
                .getStatusCode().value());
    }

    @Test
    void persistsNodeStatusAndRejectsInvalidNodes() {
        sevices.registerNode(new Sevices.NodeRegistration("node-1", "http://localhost:8545", 11155111L));
        sevices.updateNodeStatus("node-1", new Sevices.NodeStatusUpdate(BlockchainNode.Status.SYNCING));
        assertEquals(BlockchainNode.Status.SYNCING, sevices.getNode("node-1").getStatus());
        assertThrows(IllegalArgumentException.class, () -> sevices.registerNode(
                new Sevices.NodeRegistration("bad", "file:///tmp/node", 1L)));
        assertEquals(1, nodes.count());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> sevices.getNode("missing"))
                .getStatusCode().value());
    }
}
