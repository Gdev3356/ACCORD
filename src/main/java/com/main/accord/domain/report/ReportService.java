package com.main.accord.domain.report;

import com.main.accord.domain.account.AuthRepository;
import com.main.accord.domain.notification.Notification;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationRepository;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository       reportRepository;
    private final NotificationRepository notificationRepository;
    private final AuthRepository         authRepository;  // to find admins
    private final ChatHandler            chatHandler;

    @Transactional
    public Report submit(UUID reporterId, UUID reportedId, String reason) {
        Report report = reportRepository.save(
                Report.builder()
                        .idReporter(reporterId)
                        .idReported(reportedId)
                        .dsReason(reason)
                        .build()
        );

        // Notify all admins via the existing notification system
        authRepository.findAllAdmins().forEach(adminId -> {
            notificationRepository.save(
                    Notification.builder()
                            .idUser(adminId)
                            .tpNotif(NotifType.system)
                            .dsTitle("New User Report")
                            .dsBody("A user has been reported. Reason: " + (reason != null ? reason : "No reason given"))
                            .jsPayload(Map.of(
                                    "reportId",   report.getIdReport().toString(),
                                    "reporterId", reporterId.toString(),
                                    "reportedId", reportedId.toString()
                            ))
                            .build()
            );

            chatHandler.sendToUser(adminId, Map.of(
                    "type", "NEW_REPORT",
                    "data", Map.of("reportId", report.getIdReport())
            ));
        });

        return report;
    }

    @Transactional
    public Report review(UUID adminId, UUID reportId, String action) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        report.setStStatus(action); // "actioned" or "dismissed"
        report.setIdReviewedBy(adminId);
        report.setDtReviewed(OffsetDateTime.now());
        return reportRepository.save(report);
    }

    public List<Report> getPending() { return reportRepository.findAllPending(); }
    public List<Report> getAll()     { return reportRepository.findAllOrdered(); }
}