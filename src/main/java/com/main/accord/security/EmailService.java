package com.main.accord.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${accord.frontend.url}")
    private String frontendUrl;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String link = frontendUrl + "/verify?token=" + token;

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { background: #0b0b0e; color: #e6e6f0; font-family: 'Outfit', sans-serif; margin: 0; padding: 40px 20px; }
                .card { background: #111116; border: 1px solid #22222d; border-radius: 18px; max-width: 480px; margin: 0 auto; padding: 40px; }
                .logo { font-size: 28px; font-weight: 300; letter-spacing: .12em; color: #e6e6f0; margin-bottom: 8px; }
                .tagline { font-size: 13px; color: #4e4e62; margin-bottom: 32px; }
                p { font-size: 14px; color: #a1a1b5; line-height: 1.6; margin: 0 0 24px; }
                .btn { display: inline-block; background: #6ee7b7; color: #081a10; font-weight: 600; font-size: 14px; letter-spacing: .04em; padding: 12px 32px; border-radius: 10px; text-decoration: none; }
                .footer { margin-top: 32px; font-size: 12px; color: #2a2a36; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="logo">accord</div>
                <div class="tagline">find your harmony</div>
                <p>Someone used this email to create an Accord account. If that was you, confirm your address to activate your account.</p>
                <a href="%s" class="btn">Confirm email</a>
                <p style="margin-top: 24px; font-size: 12px; color: #4e4e62;">
                  This link expires in 24 hours. If you didn't sign up for Accord, you can safely ignore this email.
                </p>
                <div class="footer">No facial verification. No Persona. Just you.</div>
              </div>
            </body>
            </html>
            """.formatted(link);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("Accord <accord.official271@gmail.com>");
            helper.setTo(toEmail);
            helper.setSubject("Confirm your Accord account");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            // Log but don't crash — user can request a resend
            System.err.println("Failed to send verification email to " + toEmail + ": " + e.getMessage());
        }
    }
}