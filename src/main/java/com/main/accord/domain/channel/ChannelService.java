package com.main.accord.domain.channel;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository  channelRepository;
    private final MemberRepository   memberRepository;
    private final PermissionService  permissionService;

    public List<Channel> getChannels(UUID serverId, UUID requesterId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, requesterId)) {
            throw new ForbiddenException("You are not a member of this server.");
        }
        return channelRepository.findByIdServerOrderByNrPositionAsc(serverId);
    }

    @Transactional
    public Channel createChannel(UUID serverId, UUID requesterId, CreateChannelRequest req) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_CHANNELS);

        if (channelRepository.existsByIdServerAndDsName(serverId, req.name())) {
            throw new AccordException("A channel with that name already exists.");
        }

        // Position = end of list by default
        List<Channel> existing = channelRepository.findByIdServerOrderByNrPositionAsc(serverId);
        short nextPosition = existing.isEmpty()
                ? 0
                : (short) (existing.get(existing.size() - 1).getNrPosition() + 1);

        return channelRepository.save(
                Channel.builder()
                        .idServer(serverId)
                        .idParent(req.parentId())
                        .dsName(req.name())
                        .dsTopic(req.topic())
                        .tpChannel(req.type() != null ? req.type() : ChannelType.text)
                        .nrPosition(nextPosition)
                        .stNsfw(req.nsfw() != null && req.nsfw())
                        .build()
        );
    }

    @Transactional
    public Channel updateChannel(UUID serverId, UUID channelId, UUID requesterId, UpdateChannelRequest req) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_CHANNELS);

        Channel channel = channelRepository.findByIdChannelAndIdServer(channelId, serverId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (req.name()     != null) channel.setDsName(req.name());
        if (req.topic()    != null) channel.setDsTopic(req.topic());
        if (req.nsfw()     != null) channel.setStNsfw(req.nsfw());
        if (req.position() != null) channel.setNrPosition(req.position());
        if (req.parentId() != null) channel.setIdParent(req.parentId());

        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(UUID serverId, UUID channelId, UUID requesterId) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_CHANNELS);

        channelRepository.findByIdChannelAndIdServer(channelId, serverId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        channelRepository.deleteById(channelId);
    }

    private void assertPermission(UUID userId, UUID serverId, long permission) {
        if (!permissionService.can(userId, null, serverId, permission)) {
            throw new ForbiddenException("You don't have permission to manage channels.");
        }
    }

    public record CreateChannelRequest(
            String      name,
            String      topic,
            ChannelType type,
            UUID        parentId,
            Boolean     nsfw
    ) {}

    public record UpdateChannelRequest(
            String  name,
            String  topic,
            Boolean nsfw,
            Short   position,
            UUID    parentId
    ) {}
}