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
        UserEntity user = userRepository.findById(chatId).orElse(new UserEntity());
        user.setChatId(chatId);
        if (username != null && !username.isBlank()) user.setUsername(username);
        if (firstName != null && !firstName.isBlank()) user.setNameTelegram(firstName);
        if (phone != null && !phone.isBlank()) user.setPhone(phone);
        if (user.getIsPaid() == null) user.setIsPaid(false);
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
        Optional<UserEntity> optionalUser = userRepository.findByChatId(chatId);
        if (optionalUser.isEmpty()) return false;

        UserEntity user = optionalUser.get();

        // Если пользователь оплатил — доступ навсегда
        if (user.getIsPaid() != null && user.getIsPaid()) return true;

        // Если пользователь ещё не достиг лимита 50 тестов
        return user.getSolvedCount() < 50;
    }

    public String increaseSolvedCountAndCheckLimit(String chatId, int count) {
        Optional<UserEntity> optionalUser = userRepository.findByChatId(chatId);
        if (optionalUser.isEmpty()) return null;

        UserEntity user = optionalUser.get();
        user.setSolvedCount(user.getSolvedCount() + count);
        userRepository.save(user);

        // Если достиг 50 тестов — вернуть уведомление
        if (user.getSolvedCount() >= 50) {
            return "🚫 Сиз 50 та саволни бепул ҳал қилдингиз.\n" +
                    "Давом этиш учун обунани тўланг.";
        }

        // Иначе возвращаем null (уведомление не нужно)
        return null;
    }



    public void increaseSolvedCount(String chatId, int count) {
        userRepository.findByChatId(chatId).ifPresent(user -> {
            user.setSolvedCount(user.getSolvedCount() + count);
            userRepository.save(user);
        });
    }


    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }


    public String getAccessStatus(String chatId) {
        Optional<UserEntity> opt = userRepository.findById(chatId);
        if (opt.isEmpty()) return "Новый пользователь";

        UserEntity user = opt.get();

        // Если пользователь оплатил
        if (user.getIsPaid() != null && user.getIsPaid()) {
            return "✅ Доступ доимий фаол (тўланган)";
        }

        // Если пользователь ещё не решал тесты
        if (user.getSolvedCount() == 0) {
            return "🎁 Бепул давр мавжуд (50 та тест)";
        }

        // Если пользователь ещё в рамках бесплатных 50 тестов
        if (user.getSolvedCount() < 50) {
            int remaining = 50 - user.getSolvedCount();
            return "🎁 Бепул давр: " + remaining + " та тест қолди";
        }

        // Если пользователь исчерпал 50 тестов
        return "🚫 Сиз 50 та саволни бепул ҳал қилдингиз.\n" +
                "Давом этиш учун обунани тўланг.";
    }

    public boolean exists(String chatId) {
        return userRepository.existsByChatId(chatId);
    }

    public void deleteUser(String chatId) {
        userRepository.deleteById(chatId);
    }


}