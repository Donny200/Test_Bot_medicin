package uz.pdp.test_bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.pdp.test_bot.entity.UserProgress;
import uz.pdp.test_bot.repository.UserProgressRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProgressService {

    private final UserProgressRepository repository;

    public Optional<UserProgress> getProgress(String chatId) {
        return repository.findByChatId(chatId);
    }

    public void saveProgress(String chatId, int score, int currentQuestion, String specialty, Integer orDefault) {
        UserProgress progress = repository.findByChatId(chatId).orElse(new UserProgress());
        progress.setChatId(chatId);
        progress.setScore(score);
        progress.setCurrentQuestion(currentQuestion);
        progress.setSelectedSpecialty(specialty);
        repository.save(progress);
    }

    public void clearProgress(String chatId) {
        repository.findByChatId(chatId).ifPresent(repository::delete);
    }
}
