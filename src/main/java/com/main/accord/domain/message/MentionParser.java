package com.main.accord.domain.message;

import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.server.Member;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.domain.server.Role;
import com.main.accord.domain.server.RoleRepository;
import com.main.accord.domain.server.MemberRoleRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MentionParser {

    // Matches @role:RoleName or @user:somehandle
    private static final Pattern ROLE_PATTERN = Pattern.compile("@role:([\\w\\s-]{1,50})");
    private static final Pattern USER_PATTERN = Pattern.compile("@user:([\\w.]{1,30})");

    private final AccountRepository    accountRepository;
    private final RoleRepository       roleRepository;
    private final MemberRepository     memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final PermissionService    permissionService;

    public MentionResult parse(String content, UUID authorId, UUID channelId, UUID serverId) {
        Set<UUID> mentionedUsers = new LinkedHashSet<>();
        Set<UUID> mentionedRoles = new LinkedHashSet<>();
        boolean   everyonePinged = false;

        boolean canMentionEveryone = permissionService.can(
                authorId, channelId, serverId, Permissions.MENTION_EVERYONE
        );

        // ── @everyone / @here ──────────────────────────────────────────────────
        if (content.contains("@everyone") || content.contains("@here")) {
            if (canMentionEveryone) {
                everyonePinged = true;
                // Collect all member IDs for notification dispatch
                memberRepository.findByIdServer(serverId)
                        .stream()
                        .map(Member::getIdUser)
                        .forEach(mentionedUsers::add);
            } else {
                // Neutralise — insert zero-width space so it displays but doesn't ping
                content = content
                        .replace("@everyone", "@\u200beveryone")
                        .replace("@here",     "@\u200bhere");
            }
        }

        // ── @role:RoleName ─────────────────────────────────────────────────────
        Matcher roleMatcher = ROLE_PATTERN.matcher(content);
        StringBuffer roleResult = new StringBuffer();
        while (roleMatcher.find()) {
            String roleName = roleMatcher.group(1).trim();
            Optional<Role> roleOpt = roleRepository
                    .findByIdServerOrderByNrPositionDesc(serverId)
                    .stream()
                    .filter(r -> r.getDsName().equalsIgnoreCase(roleName) && r.getStMentionable())
                    .findFirst();

            if (roleOpt.isPresent()) {
                Role role = roleOpt.get();
                mentionedRoles.add(role.getIdRole());

                // Collect all members with this role for notifications
                memberRoleRepository.findRolesByMember(authorId, serverId); // warm cache
                memberRepository.findByIdServer(serverId).stream()
                        .map(Member::getIdUser)
                        .filter(uid -> memberRoleRepository
                                .findRolesByMember(uid, serverId)
                                .stream()
                                .anyMatch(r -> r.getIdRole().equals(role.getIdRole())))
                        .forEach(mentionedUsers::add);

                // Replace with a clean token the client renders as a pill
                roleMatcher.appendReplacement(roleResult,
                        "<role:" + role.getIdRole() + ">");
            } else {
                // Role not found or not mentionable — neutralise
                roleMatcher.appendReplacement(roleResult,
                        "@\u200b" + roleName);
            }
        }
        roleMatcher.appendTail(roleResult);
        content = roleResult.toString();

        // ── @user:handle ───────────────────────────────────────────────────────
        Matcher userMatcher = USER_PATTERN.matcher(content);
        StringBuffer userResult = new StringBuffer();
        while (userMatcher.find()) {
            String handle = userMatcher.group(1).trim();
            Optional<Account> accountOpt = accountRepository
                    .findByDsHandleIgnoreCase(handle);

            if (accountOpt.isPresent()) {
                Account account = accountOpt.get();
                // Only ping if they're actually in the server
                boolean isMember = memberRepository
                        .existsByIdServerAndIdUser(serverId, account.getIdUser());

                if (isMember) {
                    mentionedUsers.add(account.getIdUser());
                    userMatcher.appendReplacement(userResult,
                            "<user:" + account.getIdUser() + ">");
                } else {
                    userMatcher.appendReplacement(userResult,
                            "@\u200b" + handle);
                }
            } else {
                userMatcher.appendReplacement(userResult,
                        "@\u200b" + handle);
            }
        }
        userMatcher.appendTail(userResult);
        content = userResult.toString();

        return new MentionResult(content, mentionedUsers, mentionedRoles, everyonePinged);
    }

    public record MentionResult(
            String    sanitizedContent,
            Set<UUID> mentionedUserIds,
            Set<UUID> mentionedRoleIds,
            boolean   everyonePinged
    ) {}
}