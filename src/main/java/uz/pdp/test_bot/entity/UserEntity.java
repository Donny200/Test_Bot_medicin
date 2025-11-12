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

        private int solvedCount = 0;


        private String nameTelegram;  // имя из Telegram

        private String phone;         // телефон (если бот его запросит через кнопку)

        @Column(nullable = false)
        private Boolean isPaid = false; // true - оплачено навсегда

        private LocalDateTime createdAt = LocalDateTime.now();

        private LocalDateTime firstTestDate; // дата первого бесплатного теста

        private LocalDateTime paymentDate; // дата оплаты
    }