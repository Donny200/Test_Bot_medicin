package uz.pdp.test_bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payment")
@Getter
@Setter
public class PaymentConfig {
    private double subscriptionPrice = 100000;
    private String cardNumber = "5614 6820 0690 9925";
    private String cardOwner = "Xudayarov Doniyorbek";
    private String telegramUsername = "@zdzddx"; // Укажите ваш Telegram username для отправки чеков
}