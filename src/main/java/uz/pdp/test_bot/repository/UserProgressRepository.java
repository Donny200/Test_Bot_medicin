package uz.pdp.test_bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.pdp.test_bot.entity.UserProgress;

import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {
    Optional<UserProgress> findByChatId(String chatId);
}
