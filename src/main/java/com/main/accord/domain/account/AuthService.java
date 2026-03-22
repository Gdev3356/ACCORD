package com.main.accord.domain.account;

import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import com.main.accord.security.EmailService;
import com.main.accord.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository              authRepository;
    private final AccountRepository           accountRepository;
    private final VisualsRepository           visualsRepository;
    private final PlatformInviteRepository    platformInviteRepository;
    private final RefreshTokenRepository      refreshTokenRepository;
    private final EmailVerifyTokenRepository  emailVerifyTokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final JwtService                  jwtService;
    private final EmailService                emailService;

    public record RegisterRequest(
            String email,
            String password,
            String handle,
            String displayName,
            String inviteCode
    ) {}

    public record LoginRequest(String email, String password) {}

    public record AuthResponse(
            String     accessToken,
            String     refreshToken,
            AccountDto account
    ) {}

    public record AccountDto(
            String  idUser,
            String  dsHandle,
            String  dsDisplayName,
            boolean isAdmin
    ) {}

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public String register(RegisterRequest req) {
        // 1. Validate invite code
        PlatformInvite invite = platformInviteRepository.findByDsCode(req.inviteCode())
                .orElseThrow(() -> new AccordException("Invalid invite code."));

        if (invite.getStUsed()) {
            throw new AccordException("This invite has already been used.");
        }
        if (invite.getDtExpires() != null && invite.getDtExpires().isBefore(OffsetDateTime.now())) {
            throw new AccordException("This invite has expired.");
        }

        // 2. Check availability
        if (authRepository.existsByDsEmail(req.email().toLowerCase())) {
            throw new AccordException("An account with this email already exists.");
        }
        if (accountRepository.existsByDsHandle(req.handle().toLowerCase())) {
            throw new AccordException("This handle is already taken.");
        }

        // 3. Create AC_AUTH — inactive until email confirmed
        Auth auth = authRepository.save(
                Auth.builder()
                        .dsEmail(req.email().toLowerCase())
                        .dsPassword(passwordEncoder.encode(req.password()))
                        .stActive(false)   // ← inactive until verified
                        .build()
        );

        // 4. Create AC_ACCOUNT
        Account account = accountRepository.save(
                Account.builder()
                        .idUser(auth.getIdUser())
                        .dsHandle(req.handle().toLowerCase())
                        .dsDisplayName(req.displayName())
                        .build()
        );

        // 5. Create AC_VISUALS
        visualsRepository.save(
                Visuals.builder().idUser(auth.getIdUser()).build()
        );

        // 6. Mark invite used
        invite.setStUsed(true);
        invite.setIdUsedBy(auth.getIdUser());
        invite.setDtUsed(OffsetDateTime.now());
        platformInviteRepository.save(invite);

        // 7. Generate verify token and send email
        String token = generateSecureToken();
        emailVerifyTokenRepository.save(
                EmailVerifyToken.builder()
                        .idUser(auth.getIdUser())
                        .dsToken(token)
                        .dtExpires(OffsetDateTime.now().plusHours(24))
                        .build()
        );
        emailService.sendVerificationEmail(auth.getDsEmail(), token);

        // Return message — no tokens yet, account not active
        return "Account created. Check your email to confirm your address.";
    }

    // ── Verify email ──────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse verify(String token) {
        EmailVerifyToken verifyToken = emailVerifyTokenRepository.findByDsToken(token)
                .orElseThrow(() -> new AccordException("Invalid verification link."));

        if (verifyToken.getStUsed()) {
            throw new AccordException("This link has already been used.");
        }
        if (verifyToken.getDtExpires().isBefore(OffsetDateTime.now())) {
            throw new AccordException("This link has expired. Please register again.");
        }

        // Activate the account
        Auth auth = authRepository.findById(verifyToken.getIdUser())
                .orElseThrow(() -> new NotFoundException("Account not found."));
        auth.setStActive(true);
        authRepository.save(auth);

        // Mark token used
        verifyToken.setStUsed(true);
        emailVerifyTokenRepository.save(verifyToken);

        // Issue tokens — user is now logged in automatically after verifying
        Account account = accountRepository.findById(auth.getIdUser())
                .orElseThrow(() -> new NotFoundException("Account not found."));

        return issueTokens(auth, account);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Auth auth = authRepository.findByDsEmail(req.email().toLowerCase())
                .orElseThrow(() -> new AccordException("Invalid email or password."));

        if (!auth.getStActive()) {
            throw new AccordException("Please confirm your email before signing in.");
        }
        if (!passwordEncoder.matches(req.password(), auth.getDsPassword())) {
            throw new AccordException("Invalid email or password.");
        }

        Account account = accountRepository.findById(auth.getIdUser())
                .orElseThrow(() -> new NotFoundException("Account not found."));

        account.setDtLastLogin(OffsetDateTime.now());
        accountRepository.save(account);

        return issueTokens(auth, account);
    }

    // ── Refresh, signOut — unchanged from before ──────────────────────────────

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        var refreshToken = jwtService.validateRefreshToken(refreshTokenValue);
        if (refreshToken == null) {
            throw new AccordException("Invalid or expired refresh token.");
        }

        refreshToken.setStRevoked(true);
        refreshTokenRepository.save(refreshToken);

        Auth auth = authRepository.findById(refreshToken.getIdUser())
                .orElseThrow(() -> new NotFoundException("User not found."));
        Account account = accountRepository.findById(auth.getIdUser())
                .orElseThrow(() -> new NotFoundException("Account not found."));

        return issueTokens(auth, account);
    }

    @Transactional
    public void signOut(UUID userId) {
        jwtService.revokeAllTokens(userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(Auth auth, Account account) {
        String accessToken  = jwtService.generateAccessToken(auth.getIdUser(), auth.getDsEmail(), auth.getStAdmin());
        var    refreshToken = jwtService.generateRefreshToken(auth.getIdUser());

        return new AuthResponse(
                accessToken,
                refreshToken.getDsToken(),
                new AccountDto(
                        account.getIdUser().toString(),
                        account.getDsHandle(),
                        account.getDsDisplayName(),
                        auth.getStAdmin()
                )
        );
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}