package com.main.accord.domain.account;

import com.main.accord.domain.dm.FriendshipService;
import com.main.accord.common.ApiResponse;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.domain.server.ServerRepository;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    FriendshipService friendshipService;
    AccountRepository accountRepository;
    MemberRepository memberRepository;
    ServerRepository serverRepository;

    @GetMapping("/{userId}/mutual-friends")
    public ResponseEntity<ApiResponse<MutualFriendsResponse>> getMutualFriends(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        List<UUID> myFriends     = friendshipService.getFriendIds(principal.userId());
        List<UUID> theirFriends  = friendshipService.getFriendIds(userId);

        List<UUID> mutualIds = myFriends.stream()
                .filter(theirFriends::contains)
                .toList();

        List<AccountSummary> mutuals = mutualIds.stream()
                .flatMap(id -> accountRepository.findSummaryById(id).stream())
                .map(p -> new AccountSummary(p.getIdUser(), p.getDsDisplayName(), p.getDsHandle(), p.getDsPfpUrl()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(
                new MutualFriendsResponse(mutuals.size(), mutuals)
        ));
    }

    @GetMapping("/{userId}/mutual-servers")
    public ResponseEntity<ApiResponse<MutualServersResponse>> getMutualServers(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        List<UUID> myServers    = memberRepository.findServerIdsByUser(principal.userId());
        List<UUID> theirServers = memberRepository.findServerIdsByUser(userId);

        List<UUID> mutualIds = myServers.stream()
                .filter(theirServers::contains)
                .toList();

        List<ServerSummary> mutuals = mutualIds.stream()
                .flatMap(id -> serverRepository.findSummaryById(id).stream())
                .map(p -> new ServerSummary(p.getIdServer(), p.getDsName(), p.getDsIconUrl()))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(
                new MutualServersResponse(mutuals.size(), mutuals)
        ));
    }

    public record AccountSummary(UUID idUser, String dsDisplayName, String dsHandle, String dsPfpUrl) {}
    public record ServerSummary(UUID idServer, String dsName, String dsIconUrl) {}
    public record MutualFriendsResponse(int count, List<AccountSummary> users) {}
    public record MutualServersResponse(int count, List<ServerSummary> servers) {}
}
