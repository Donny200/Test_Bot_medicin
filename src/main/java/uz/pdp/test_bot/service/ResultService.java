package uz.pdp.test_bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.pdp.test_bot.entity.ResultEntity;
import uz.pdp.test_bot.repository.ResultRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResultService {

    private final ResultRepository resultRepository;

    public void saveResult(String chatId, int score, int total) {
        ResultEntity result = new ResultEntity();
        result.setChatId(chatId);
        result.setScore(score);
        result.setTotal(total);
        resultRepository.save(result);
    }

    public List<ResultEntity> getResults(String chatId) {
        return resultRepository.findByChatIdOrderByCreatedAtDesc(chatId);
    }
}