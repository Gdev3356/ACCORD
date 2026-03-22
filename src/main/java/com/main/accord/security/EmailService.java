package com.main.accord.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from}")
    private String from;

    @Value("${accord.frontend.url}")
    private String frontendUrl;

    private final RestTemplate restTemplate;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String link = frontendUrl + "/verify?token=" + token;

        String html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body{background:#0b0b0e;color:#e6e6f0;font-family:sans-serif;margin:0;padding:40px 20px}
                .card{background:#111116;border:1px solid #22222d;border-radius:18px;max-width:480px;margin:0 auto;padding:40px}
                .logo{font-size:28px;font-weight:300;letter-spacing:.12em;color:#e6e6f0;margin-bottom:8px}
                .tagline{font-size:13px;color:#4e4e62;margin-bottom:32px}
                p{font-size:14px;color:#a1a1b5;line-height:1.6;margin:0 0 24px}
                .btn{display:inline-block;background:#6ee7b7;color:#081a10;font-weight:600;font-size:14px;padding:12px 32px;border-radius:10px;text-decoration:none}
                .footer{margin-top:32px;font-size:12px;color:#2a2a36}
              </style>
            </head>
            <body>
              <div class="card">
                <div class="logo">accord</div>
                <div class="tagline">No data stealing here.</div>
                <p>Confirm your email address to activate your Accord account.</p>
                <a href="%s" class="btn">Confirm email</a>
                <p style="margin-top:24px;font-size:12px;color:#4e4e62">
                  This link expires in 24 hours.
                </p>
                <div class="footer">No facial verification. No Persona. Just you.</div>
              </div>
            </body>
            </html>
            """.formatted(link);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "from",    from,
                "to",      new String[]{ toEmail },
                "subject", "Confirm your Accord account",
                "html",    html
        );

        try {
            restTemplate.exchange(
                    "https://api.resend.com/emails",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
        } catch (Exception e) {
            System.err.println("Failed to send email to " + toEmail + ": " + e.getMessage());
        }
    }
}