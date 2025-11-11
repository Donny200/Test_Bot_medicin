package uz.pdp.test_bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.pdp.test_bot.entity.UserEntity;
import uz.pdp.test_bot.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void ensureUser(String chatId, String username, String firstName, String phone) {
        if (userRepository.existsById(chatId)) return;
        UserEntity user = new UserEntity();
        user.setChatId(chatId);
        user.setUsername(username);
        user.setNameTelegram(firstName);
        user.setPhone(phone);
        user.setIsPaid(false);
        userRepository.save(user);
    }

    public Optional<UserEntity> getUser(String chatId) {
        return userRepository.findById(chatId);
    }

    public void activatePaid(String chatId) {
        Optional<UserEntity> opt = userRepository.findById(chatId);
        if (opt.isPresent()) {
            UserEntity user = opt.get();
            user.setIsPaid(true);
            user.setPaymentDate(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    public boolean canTakeTest(String chatId) {
        Optional<UserEntity> opt = userRepository.findById(chatId);
        if (opt.isEmpty()) return false;

        UserEntity user = opt.get();

        // Если реально оплачен (isPaid=true) — доступ навсегда
        if (user.getIsPaid()) return true;

        // Проверяем бесплатный период (1 день = 24 часа)
        if (user.getFirstTestDate() == null) {
            // Первый раз — разрешаем тест и сохраняем дату
            user.setFirstTestDate(LocalDateTime.now());
            userRepository.save(user);
            return true;
        }

        // Проверяем, прошло ли 24 часа
        long hoursPassed = ChronoUnit.HOURS.between(user.getFirstTestDate(), LocalDateTime.now());
        return hoursPassed < 24;
    }

    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }


    public String getAccessStatus(String chatId) {
        Optional<UserEntity> opt = userRepository.findById(chatId);
        if (opt.isEmpty()) return "Новый пользователь";

        UserEntity user = opt.get();

        if (user.getIsPaid()) return "✅ Доступ активен навсегда (оплачено)";

        if (user.getFirstTestDate() == null)
            return "🎁 Доступен бесплатный период (1 день с первого теста)";

        long hoursPassed = ChronoUnit.HOURS.between(user.getFirstTestDate(), LocalDateTime.now());
        if (hoursPassed < 24) {
            long hoursLeft = 24 - hoursPassed;
            return "🎁 Бесплатный период: осталось " + hoursLeft + " ч";
        }

        return "❌ Бесплатный период истек. Для доступа к тестам требуется оплата.\n\n" +
                "💳 Отправьте скриншот оплаты администратору для ручной проверки.";
    }

}