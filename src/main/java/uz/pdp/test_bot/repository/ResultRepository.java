package uz.pdp.test_bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.pdp.test_bot.entity.ResultEntity;

import java.util.List;

public interface ResultRepository extends JpaRepository<ResultEntity, Long> {
    List<ResultEntity> findByChatIdOrderByCreatedAtDesc(String chatId);
}