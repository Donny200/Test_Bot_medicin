package uz.pdp.test_bot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String chatId;
    private double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING; // PENDING, CONFIRMED, REJECTED

    private String transactionId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime confirmedAt;

    public enum PaymentStatus {
        PENDING,    // Ожидает проверки
        CONFIRMED,  // Подтверждено администратором (true - доступ разрешен)
        REJECTED    // Отклонено
    }
}   