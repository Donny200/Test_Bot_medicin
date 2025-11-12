package uz.pdp.test_bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.pdp.test_bot.entity.UserEntity;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    boolean existsByChatId(String chatId);
    Optional<UserEntity> findByChatId(String chatId); // ✅ добавляем


}