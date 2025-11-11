package uz.pdp.test_bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.pdp.test_bot.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    boolean existsByChatId(String chatId);

}