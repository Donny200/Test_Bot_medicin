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
@Table(name = "users")
public class UserEntity {
    @Id
    private String chatId;

    private String username;

    @Column(nullable = false)
    private Boolean isPaid = false; // true - оплачено навсегда, доступ разрешен

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime firstTestDate; // дата первого бесплатного теста (для 24ч доступа)

    private LocalDateTime paymentDate; // дата оплаты
}