package uz.pdp.test_bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class BotConfig {

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${payment.subscription-price:100.000}")
    private double subscriptionPrice;

    @Value("${payment.card-number:5614 6820 0690 9925}")
    private String cardNumber;

    @Value("${payment.card-owner:Xudayarov Doniyorbek}")
    private String cardOwner;

    @Value("${payment.telegram-username:zdzddx}")
    private String telegramUsername;
}