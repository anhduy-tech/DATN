package com.lapxpert.backend.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {
    @Autowired
    private JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String configuredSenderEmail;

    public void sendEmail(String to, String subject, String text) {
        sendEmail(to, subject, text, configuredSenderEmail);
    }

    public void sendBulkEmail(List<String> to, String subject, String text) {
        sendBulkEmail(to, subject, text, configuredSenderEmail);
    }


    public void sendEmail(String to, String subject, String text, String from) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        emailSender.send(message);
    }


    public void sendBulkEmail(List<String> to, String subject, String text, String from) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);  // Đặt người gửi
        message.setTo(to.toArray(new String[0]));
        message.setSubject(subject);
        message.setText(text);
        emailSender.send(message);
    }

    public void sendPasswordEmail(String to, String rawPassword) {
        String subject = "Mật khẩu mới của bạn";
        String text = String.format(
                "Chào bạn,\n\n" +
                        "Mật khẩu mới của bạn là: %s\n\n" +
                        "Vui lòng không chia sẻ mật khẩu này cho bất kỳ ai để đảm bảo an toàn tài khoản.\n\n" +
                        "Trân trọng,\nLapXpert Store",
                rawPassword
        );

        sendEmail(to, subject, text);
    }

    public void sendOrderStatusUpdateEmail(String to, Long orderId, String orderCode, String oldStatus, String newStatus, String reason) {
        String subject = String.format("Cập nhật trạng thái đơn hàng #%s", orderCode);
        String orderLink = "http://localhost:5173/shop/orders/" + orderId;

        String text = String.format(
                "Chào bạn,\n\n" +
                        "Chúng tôi xin thông báo về sự thay đổi trạng thái của đơn hàng #%s của bạn.\n\n" +
                        "Trạng thái cũ: %s\n" +
                        "Trạng thái mới: %s\n" +
                        "Lý do thay đổi: %s\n\n" +
                        "Bạn có thể xem chi tiết đơn hàng của mình tại: %s\n\n" +
                        "Nếu có bất kỳ thắc mắc nào, vui lòng liên hệ với chúng tôi để được hỗ trợ.\n\n" +
                        "Trân trọng,\n" +
                        "LapXpert Store",
                orderCode,
                oldStatus,
                newStatus,
                reason != null && !reason.isEmpty() ? reason : "Không có lý do cụ thể.",
                orderLink
        );

        sendEmail(to, subject, text);
    }

}



