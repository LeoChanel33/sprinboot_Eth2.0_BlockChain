package com.example.demo.eth20.service;

import com.example.demo.eth20.entity.BlockchainNode;
import com.example.demo.eth20.entity.PlatformAccount;
import com.example.demo.eth20.entity.UserAccount;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Main application entry point; Spring injects each JPA service via the constructor. */
@Service
@RequiredArgsConstructor
@Transactional
public class Sevices {
    private final UserJpaService users;
    private final NodeJpaService nodes;
    private final CrossPlatformJpaService platforms;

    public record Registration(String username, String email, PlatformLink platform) {}
    public record PlatformLink(String platform, String externalAccountId) {}
    public record NodeRegistration(String nodeId, String rpcUrl, Long chainId) {}
    public record NodeStatusUpdate(BlockchainNode.Status status) {}
    public record UserView(Long id, String username, String email) {
        static UserView from(UserAccount user) {
            return new UserView(user.getId(), user.getUsername(), user.getEmail());
        }
    }
    public record PlatformView(Long id, Long userId, String platform, String externalAccountId) {
        static PlatformView from(PlatformAccount account) {
            return new PlatformView(account.getId(), account.getUser().getId(),
                    account.getPlatform(), account.getExternalAccountId());
        }
    }

    /** Register and optionally link a platform in one transaction; any failure rolls back both. */
    public UserView register(Registration request) {
        if (request == null) throw new IllegalArgumentException("Registration is required");
        UserAccount user = users.register(request.username(), request.email());
        if (request.platform() != null) {
            platforms.link(user.getId(), request.platform().platform(), request.platform().externalAccountId());
        }
        return UserView.from(user);
    }

    @Transactional(readOnly = true)
    public UserView getUser(Long userId) {
        return UserView.from(users.get(userId));
    }

    public BlockchainNode registerNode(NodeRegistration request) {
        if (request == null) throw new IllegalArgumentException("Node registration is required");
        return nodes.register(request.nodeId(), request.rpcUrl(), request.chainId());
    }

    @Transactional(readOnly = true)
    public BlockchainNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public BlockchainNode updateNodeStatus(String nodeId, NodeStatusUpdate request) {
        if (request == null) throw new IllegalArgumentException("Node status is required");
        return nodes.updateStatus(nodeId, request.status());
    }

    public PlatformView linkPlatform(Long userId, PlatformLink request) {
        if (request == null) throw new IllegalArgumentException("Platform link is required");
        return PlatformView.from(platforms.link(userId, request.platform(), request.externalAccountId()));
    }

    @Transactional(readOnly = true)
    public List<PlatformView> getPlatforms(Long userId) {
        return platforms.list(userId).stream().map(PlatformView::from).toList();
    }
}
