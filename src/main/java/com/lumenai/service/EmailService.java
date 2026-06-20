package com.lumenai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${lumenai.mail.from:noreply@lumenai.app}")
    private String fromAddress;

    @Value("${lumenai.app.url:http://localhost:5173}")
    private String appUrl;

    public void sendVerificationEmail(String toEmail, String name, String token) {
        String verifyUrl = appUrl + "/verify?token=" + token;

        if (!mailEnabled) {
            // In local dev, just log the link — no email sent
            log.info("╔══════════════════════════════════════════════════════╗");
            log.info("  [LumenAi] EMAIL VERIFICATION (mail disabled in dev)");
            log.info("  To:      {}", toEmail);
            log.info("  Link:    {}", verifyUrl);
            log.info("╚══════════════════════════════════════════════════════╝");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Verify your LumenAi account ✨");
            helper.setText(buildVerificationEmailHtml(name, verifyUrl), true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            // Don't throw — user is registered, they can request resend later
        }
    }

    public void sendPasswordResetEmail(String toEmail, String name, String token) {
        String resetUrl = appUrl + "/reset-password?token=" + token;

        if (!mailEnabled) {
            log.info("[LumenAi] PASSWORD RESET LINK for {}: {}", toEmail, resetUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Reset your LumenAi password 🔐");
            helper.setText(buildPasswordResetHtml(name, resetUrl), true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildVerificationEmailHtml(String name, String verifyUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background-color:#0f172a;font-family:'Inter',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;padding:40px 20px;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#1e293b;border-radius:16px;overflow:hidden;border:1px solid #334155;">
                    <!-- Header -->
                    <tr><td style="background:linear-gradient(135deg,#14b8a6,#6366f1);padding:32px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:28px;font-weight:800;letter-spacing:-0.5px;">LumenAi ✨</h1>
                      <p style="color:rgba(255,255,255,0.8);margin:8px 0 0;">AI-Powered Study Assistant</p>
                    </td></tr>
                    <!-- Body -->
                    <tr><td style="padding:40px;">
                      <h2 style="color:#f1f5f9;font-size:22px;margin:0 0 16px;">Welcome, %s! 👋</h2>
                      <p style="color:#94a3b8;font-size:15px;line-height:1.7;margin:0 0 24px;">
                        Thank you for joining LumenAi. Please verify your email address to activate your account and start your AI-powered learning journey.
                      </p>
                      <div style="text-align:center;margin:32px 0;">
                        <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#14b8a6,#6366f1);color:#fff;font-size:16px;font-weight:700;text-decoration:none;padding:14px 40px;border-radius:10px;letter-spacing:0.3px;">
                          ✅ Verify My Email
                        </a>
                      </div>
                      <p style="color:#64748b;font-size:13px;text-align:center;">
                        Or copy this link: <a href="%s" style="color:#14b8a6;">%s</a>
                      </p>
                      <hr style="border:none;border-top:1px solid #334155;margin:32px 0;">
                      <p style="color:#475569;font-size:12px;text-align:center;margin:0;">
                        This link expires in 24 hours. If you didn't create an account, please ignore this email.
                      </p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, verifyUrl, verifyUrl, verifyUrl);
    }

    private String buildPasswordResetHtml(String name, String resetUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background-color:#0f172a;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;padding:40px 20px;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0" style="background:#1e293b;border-radius:16px;border:1px solid #334155;">
                    <tr><td style="background:linear-gradient(135deg,#f43f5e,#6366f1);padding:32px;text-align:center;">
                      <h1 style="color:#fff;margin:0;font-size:28px;font-weight:800;">LumenAi 🔐</h1>
                    </td></tr>
                    <tr><td style="padding:40px;">
                      <h2 style="color:#f1f5f9;">Password Reset, %s</h2>
                      <p style="color:#94a3b8;line-height:1.7;">Click the button below to reset your password. This link is valid for 1 hour.</p>
                      <div style="text-align:center;margin:32px 0;">
                        <a href="%s" style="display:inline-block;background:#f43f5e;color:#fff;font-size:16px;font-weight:700;text-decoration:none;padding:14px 40px;border-radius:10px;">
                          🔑 Reset Password
                        </a>
                      </div>
                      <p style="color:#475569;font-size:12px;text-align:center;">If you didn't request this, you can safely ignore this email.</p>
                    </td></tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name, resetUrl);
    }
}
