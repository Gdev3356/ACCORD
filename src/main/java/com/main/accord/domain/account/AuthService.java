package com.main.accord.domain.account;

import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import com.main.accord.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository           authRepository;
    private final AccountRepository        accountRepository;
    private final VisualsRepository        visualsRepository;
    private final PlatformInviteRepository platformInviteRepository;
    private final RefreshTokenRepository   refreshTokenRepository;
    private final PasswordEncoder          passwordEncoder;
    private final JwtService               jwtService;

    public record RegisterRequest(
            String email,
            String password,
            String handle,
            String displayName,
            String inviteCode     // required — no invite, no account
    ) {}

    public record LoginRequest(String email, String password) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            AccountDto account
    ) {}

    public record AccountDto(
            String idUser,
            String dsHandle,
            String dsDisplayName
    ) {}

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // 1. Validate invite code
        PlatformInvite invite = platformInviteRepository.findByDsCode(req.inviteCode())
                .orElseThrow(() -> new AccordException("Invalid invite code."));

        if (invite.getStUsed()) {
            throw new AccordException("This invite has already been used.");
        }
        if (invite.getDtExpires() != null && invite.getDtExpires().isBefore(OffsetDateTime.now())) {
            throw new AccordException("This invite has expired.");
        }

        // 2. Check email and handle availability
        if (authRepository.existsByDsEmail(req.email())) {
            throw new AccordException("An account with this email already exists.");
        }
        if (accountRepository.existsByDsHandle(req.handle().toLowerCase())) {
            throw new AccordException("This handle is already taken.");
        }

        // 3. Create AC_AUTH (credentials)
        Auth auth = authRepository.save(
                Auth.builder()
                        .dsEmail(req.email().toLowerCase())
                        .dsPassword(passwordEncoder.encode(req.password()))
                        .build()
        );

        // 4. Create AC_ACCOUNT (profile)
        Account account = accountRepository.save(
                Account.builder()
                        .idUser(auth.getIdUser())
                        .dsHandle(req.handle().toLowerCase())
                        .dsDisplayName(req.displayName())
                        .build()
        );

        // 5. Create AC_VISUALS (defaults)
        visualsRepository.save(
                Visuals.builder()
                        .idUser(auth.getIdUser())
                        .build()
        );

        // 6. Mark invite as used
        invite.setStUsed(true);
        invite.setIdUsedBy(auth.getIdUser());
        invite.setDtUsed(OffsetDateTime.now());
        platformInviteRepository.save(invite);

        // 7. Issue tokens
        return issueTokens(auth, account);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Auth auth = authRepository.findByDsEmail(req.email().toLowerCase())
                .orElseThrow(() -> new AccordException("Invalid email or password."));

        if (!auth.getStActive()) {
            throw new AccordException("This account has been deactivated.");
        }
        if (!passwordEncoder.matches(req.password(), auth.getDsPassword())) {
            throw new AccordException("Invalid email or password.");
        }

        Account account = accountRepository.findById(auth.getIdUser())
                .orElseThrow(() -> new NotFoundException("Account not found."));

        // Bump last login
        account.setDtLastLogin(OffsetDateTime.now());
        accountRepository.save(account);

        return issueTokens(auth, account);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        var refreshToken = jwtService.validateRefreshToken(refreshTokenValue);
        if (refreshToken == null) {
            throw new AccordException("Invalid or expired refresh token.");
        }

        // Rotate — revoke old, issue new
        refreshToken.setStRevoked(true);
        refreshTokenRepository.save(refreshToken);

        Auth auth = authRepository.findById(refreshToken.getIdUser())
                .orElseThrow(() -> new NotFoundException("User not found."));
        Account account = accountRepository.findById(auth.getIdUser())
                .orElseThrow(() -> new NotFoundException("Account not found."));

        return issueTokens(auth, account);
    }

    // ── Sign out ──────────────────────────────────────────────────────────────

    @Transactional
    public void signOut(UUID userId) {
        jwtService.revokeAllTokens(userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(Auth auth, Account account) {
        String accessToken  = jwtService.generateAccessToken(auth.getIdUser(), auth.getDsEmail());
        var    refreshToken = jwtService.generateRefreshToken(auth.getIdUser());

        return new AuthResponse(
                accessToken,
                refreshToken.getDsToken(),
                new AccountDto(
                        account.getIdUser().toString(),
                        account.getDsHandle(),
                        account.getDsDisplayName()
                )
        );
    }
}