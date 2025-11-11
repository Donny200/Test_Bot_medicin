package uz.pdp.test_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.pdp.test_bot.bot.MyBot;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        try {
            var ctx = SpringApplication.run(Application.class, args);

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            MyBot myBot = ctx.getBean(MyBot.class);
            botsApi.registerBot(myBot);

            System.out.println("🤖 Telegram бот успешно запущен и слушает обновления...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

