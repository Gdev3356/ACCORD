package com.main.accord.domain.game;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.AuthRepository;
import com.main.accord.domain.notification.Notification;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationRepository;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AbLevelService {

    private final AbLevelRepository             levelRepository;
    private final AbScoreRepository             scoreRepository;
    private final AbLevelVoteRepository         voteRepository;
    private final AbLevelCommentRepository      commentRepository;
    private final AbLevelReportRepository       reportRepository;
    private final GmAchievementRepository       achievementRepository;
    private final GmPlayerAchievementRepository playerAchievementRepository;
    private final NotificationRepository        notificationRepository;
    private final AuthRepository                authRepository;
    private final ChatHandler                   chatHandler;

    // FIX: allowed values for report review actions — prevents arbitrary strings
    //      being persisted to ST_STATUS.
    private static final Set<String> VALID_REPORT_ACTIONS =
            Set.of("pending", "reviewed", "actioned", "dismissed");

    // Default page size for the published-levels listing.
    // Keeps the response payload small on Render.com's 512 MB free tier.
    private static final int PUBLISHED_PAGE_SIZE = 50;

    // ── Level CRUD ────────────────────────────────────────────────────────────

    /**
     * Save a draft. Creator may iterate freely before publishing.
     *
     * FIX: removed _tryUnlock("ab.creator") from here — the achievement
     *      description is "Publish your first level", so it belongs in publish().
     */
    @Transactional
    public AbLevel saveDraft(UUID creatorId, SaveLevelRequest req) {
        if (req.parScore() < 1)
            throw new AccordException("Par score must be at least 1.");

        return levelRepository.save(AbLevel.builder()
                .idCreator(creatorId)
                .dsName(req.name())
                .dsDesc(req.description())
                .jsData(req.data())
                .nrParScore(req.parScore())
                .build());
    }

    /**
     * Update an existing draft or unpublished level.
     * A published level's JS_DATA is frozen — the creator must fork it.
     */
    @Transactional
    public AbLevel updateDraft(UUID requesterId, UUID levelId, SaveLevelRequest req) {
        if (req.parScore() < 1)
            throw new AccordException("Par score must be at least 1.");

        AbLevel level = _requireOwner(requesterId, levelId);
        if (level.getStPublished())
            throw new AccordException("Published levels cannot be edited. Fork the level to create a new version.");

        level.setDsName(req.name());
        level.setDsDesc(req.description());
        level.setJsData(req.data());
        level.setNrParScore(req.parScore());
        return levelRepository.save(level);
    }

    /**
     * Publish a level.
     * Requires: creator must have a score row with NR_SCORE >= NR_PAR_SCORE.
     *
     * FIX: _tryUnlock("ab.creator") moved here from saveDraft() — the
     *      achievement fires when the level is actually published, not drafted.
     */
    @Transactional
    public AbLevel publish(UUID creatorId, UUID levelId) {
        AbLevel level = _requireOwner(creatorId, levelId);
        if (level.getStPublished())
            throw new AccordException("Level is already published.");

        boolean beaten = levelRepository.creatorHasBeatenLevel(levelId, creatorId, level.getNrParScore());
        if (!beaten)
            throw new AccordException(
                    "You must beat your own level (score ≥ " + level.getNrParScore() + ") before publishing."
            );

        level.setStPublished(true);
        AbLevel saved = levelRepository.save(level);

        // FIX: achievement fires on publish, not on draft save
        _tryUnlock(creatorId, "ab.creator");
        return saved;
    }

    /** Soft-delete. Creator or admin only. */
    @Transactional
    public void delete(UUID requesterId, UUID levelId, boolean isAdmin) {
        AbLevel level = levelRepository.findById(levelId)
                .orElseThrow(() -> new NotFoundException("Level not found."));
        if (!isAdmin && !level.getIdCreator().equals(requesterId))
            throw new ForbiddenException("You can only delete your own levels.");
        level.setStDeleted(true);
        levelRepository.save(level);
    }

    // FIX: returns a bounded page instead of the entire table; callers that
    //      previously used getPublished() must now pass a page number.
    @Transactional(readOnly = true)
    public List<AbLevel> getPublished(int page) {
        return levelRepository.findAllPublished(PageRequest.of(page, PUBLISHED_PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public List<AbLevel> getByCreator(UUID userId) { return levelRepository.findByCreator(userId); }

    @Transactional(readOnly = true)
    public AbLevel getById(UUID levelId) { return _requireExists(levelId); }

    // ── Score submission ──────────────────────────────────────────────────────

    /**
     * Submit a score. Keeps personal best (highest NR_SCORE per user per level).
     * Triggers relevant achievement checks after saving.
     */
    @Transactional
    public AbScore submitScore(UUID userId, UUID levelId, int score, short stars) {
        AbLevel level = _requireExists(levelId);
        if (!level.getStPublished() && !level.getIdCreator().equals(userId))
            throw new ForbiddenException("That level is not published yet.");

        var existing = scoreRepository.findByIdLevelAndIdUser(levelId, userId);

        AbScore saved;
        if (existing.isPresent()) {
            AbScore current = existing.get();
            if (score <= current.getNrScore()) return current;   // not a personal best
            current.setNrScore(score);
            current.setNrStars((short) Math.max(stars, current.getNrStars()));
            current.setDtAchieved(OffsetDateTime.now());
            saved = scoreRepository.save(current);
        } else {
            saved = scoreRepository.save(AbScore.builder()
                    .idLevel(levelId).idUser(userId)
                    .nrScore(score).nrStars(stars)
                    .build());
        }

        _checkScoreAchievements(userId, saved);
        return saved;
    }

    /** Top-100 leaderboard for a level page. */
    @Transactional(readOnly = true)
    public List<AbScore> getLeaderboard(UUID levelId) {
        _requireExists(levelId);
        return scoreRepository.findLeaderboard(levelId, PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true)
    public AbScore getUserScore(UUID userId, UUID levelId) {
        _requireExists(levelId); // Ensure the level actually exists first
        return scoreRepository.findByIdLevelAndIdUser(levelId, userId)
                .orElse(null);
    }
    // ── Votes ─────────────────────────────────────────────────────────────────

    /**
     * Cast or change a vote. Notifies the level creator when a new upvote lands.
     *
     * FIX: replaced `v.getStUpvote() == upvote` (boxed Boolean reference
     *      equality) with Objects.equals() to avoid both the wrong result and
     *      a potential NullPointerException.
     */
    @Transactional
    public void vote(UUID userId, UUID levelId, boolean upvote) {
        AbLevel level = _requireExists(levelId);
        if (!level.getStPublished())
            throw new AccordException("You can only vote on published levels.");

        var existing = voteRepository.findByIdLevelAndIdUser(levelId, userId);
        boolean isNewUpvote = false;

        if (existing.isPresent()) {
            AbLevelVote v = existing.get();
            // FIX: Objects.equals handles the boxed Boolean safely
            if (Objects.equals(v.getStUpvote(), upvote)) {
                voteRepository.delete(v);   // toggle off
                return;
            }
            isNewUpvote = upvote && !Boolean.TRUE.equals(v.getStUpvote());
            v.setStUpvote(upvote);
            voteRepository.save(v);
        } else {
            isNewUpvote = upvote;
            voteRepository.save(AbLevelVote.builder()
                    .idLevel(levelId).idUser(userId).stUpvote(upvote).build());
        }

        // Notify creator of a new upvote (not for own levels, not for downvotes)
        if (isNewUpvote && !level.getIdCreator().equals(userId)) {
            _notify(level.getIdCreator(), NotifType.level_vote,
                    "Your level got an upvote!",
                    "Someone upvoted \"" + level.getDsName() + "\".",
                    Map.of("levelId", levelId.toString(), "voterId", userId.toString()));
        }
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    /**
     * FIX: ab.critic now checks the actual comment count (>= 10) instead of
     *      firing on the very first comment.
     */
    @Transactional
    public AbLevelComment addComment(UUID userId, UUID levelId, String content) {
        AbLevel level = _requireExists(levelId);
        if (!level.getStPublished())
            throw new AccordException("You can only comment on published levels.");
        if (content == null || content.isBlank())
            throw new AccordException("Comment cannot be empty.");
        if (content.length() > 500)
            throw new AccordException("Comment must be 500 characters or fewer.");

        AbLevelComment comment = commentRepository.save(AbLevelComment.builder()
                .idLevel(levelId).idUser(userId).dsContent(content).build());

        // Notify creator (unless commenting on your own level)
        if (!level.getIdCreator().equals(userId)) {
            _notify(level.getIdCreator(), NotifType.level_comment,
                    "New comment on your level",
                    "Someone commented on \"" + level.getDsName() + "\".",
                    Map.of("levelId",   levelId.toString(),
                            "commentId", comment.getIdComment().toString()));
        }

        // FIX: count non-deleted comments by this user; unlock only at 10
        long commentCount = commentRepository.countByIdUserAndStDeletedFalse(userId);
        if (commentCount >= 10) _tryUnlock(userId, "ab.critic");

        return comment;
    }

    @Transactional
    public void deleteComment(UUID requesterId, UUID commentId, boolean isAdmin) {
        AbLevelComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found."));
        if (!isAdmin && !c.getIdUser().equals(requesterId))
            throw new ForbiddenException("You can only delete your own comments.");
        c.setStDeleted(true);
        commentRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<AbLevelComment> getComments(UUID levelId) {
        _requireExists(levelId);
        return commentRepository.findByLevel(levelId);
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @Transactional
    public AbLevelReport reportLevel(UUID reporterId, UUID levelId, String reason) {
        _requireExists(levelId);
        if (reportRepository.existsByIdLevelAndIdReporter(levelId, reporterId))
            throw new AccordException("You have already reported this level.");

        AbLevelReport report = reportRepository.save(AbLevelReport.builder()
                .idLevel(levelId).idReporter(reporterId).dsReason(reason).build());

        authRepository.findAllAdmins().forEach(adminId ->
                _notify(adminId, NotifType.system,
                        "Level Report",
                        "A level has been reported.",
                        Map.of("reportId",   report.getIdReport().toString(),
                                "levelId",    levelId.toString(),
                                "reporterId", reporterId.toString()))
        );
        return report;
    }

    /**
     * FIX: validate the action string against the allowed set before persisting,
     *      preventing arbitrary values from landing in ST_STATUS.
     */
    @Transactional
    public AbLevelReport reviewReport(UUID adminId, UUID reportId, String action) {
        if (!VALID_REPORT_ACTIONS.contains(action))
            throw new AccordException("Invalid report action: " + action +
                    ". Allowed: " + VALID_REPORT_ACTIONS);

        AbLevelReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found."));
        report.setStStatus(action);
        report.setIdReviewedBy(adminId);
        report.setDtReviewed(OffsetDateTime.now());
        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<AbLevelReport> getPendingReports() { return reportRepository.findPending(); }

    // ── Achievement helpers ───────────────────────────────────────────────────

    /**
     * FIX: 3-star completion now unlocks "ab.perfect_score" (correct key).
     *      "ab.perfectionist" ("Beat every level by one creator") is a
     *      complex achievement that cannot be checked here.
     */
    private void _checkScoreAchievements(UUID userId, AbScore score) {
        _tryUnlock(userId, "ab.level_complete");
        if (score.getNrStars() == 3) _tryUnlock(userId, "ab.perfect_score");
        // Hotshot: are they sitting at rank 1?
        List<AbScore> top = scoreRepository.findLeaderboard(score.getIdLevel(), PageRequest.of(0, 1));
        if (!top.isEmpty() && top.get(0).getIdUser().equals(userId))
            _tryUnlock(userId, "ab.hotshot");
    }

    /**
     * Unlock an achievement by key if the user doesn't already have it,
     * then fire a real-time notification over WebSocket.
     */
    private void _tryUnlock(UUID userId, String key) {
        achievementRepository.findByDsKey(key).ifPresent(ach -> {
            if (!playerAchievementRepository.existsByIdUserAndIdAchievement(userId, ach.getIdAchievement())) {
                playerAchievementRepository.save(GmPlayerAchievement.builder()
                        .idUser(userId).idAchievement(ach.getIdAchievement()).build());

                _notify(userId, NotifType.achievement_unlocked,
                        "Achievement Unlocked: " + ach.getDsTitle(),
                        ach.getDsDesc(),
                        Map.of("achievementKey", key));
            }
        });
    }

    // ── Notification helper ───────────────────────────────────────────────────

    private void _notify(UUID target, NotifType type, String title, String body, Map<String, Object> payload) {
        notificationRepository.save(Notification.builder()
                .idUser(target).tpNotif(type)
                .dsTitle(title).dsBody(body)
                .jsPayload(payload).build());

        chatHandler.sendToUser(target, Map.of(
                "type", "NOTIFICATION",
                "data", Map.of("tpNotif", type.name(), "dsTitle", title, "jsPayload", payload)
        ));
    }

    // ── Guard helpers ─────────────────────────────────────────────────────────

    private AbLevel _requireExists(UUID levelId) {
        return levelRepository.findById(levelId)
                .filter(l -> !l.getStDeleted())
                .orElseThrow(() -> new NotFoundException("Level not found."));
    }

    private AbLevel _requireOwner(UUID userId, UUID levelId) {
        AbLevel level = _requireExists(levelId);
        if (!level.getIdCreator().equals(userId))
            throw new ForbiddenException("You don't own this level.");
        return level;
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record SaveLevelRequest(
            String              name,
            String              description,
            Map<String, Object> data,
            int                 parScore
    ) {}
}