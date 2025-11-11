package uz.pdp.test_bot.service;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TestService {

    private final List<Question> questions = new ArrayList<>();

    public TestService() {
        questions.add(new Question("Какой витамин синтезируется под действием солнца?",
                List.of("A", "B12", "D", "C"), 2));
        questions.add(new Question("Сколько камер в сердце человека?",
                List.of("2", "3", "4", "5"), 2));
    }

    public Optional<Question> getQuestion(int num) {
        if (num <= 0 || num > questions.size()) return Optional.empty();
        return Optional.of(questions.get(num - 1));
    }

    public int totalQuestions() {
        return questions.size();
    }

    public static class Question {
        private final String text;
        private final List<String> options;
        private final int correctIndex;

        public Question(String text, List<String> options, int correctIndex) {
            this.text = text;
            this.options = options;
            this.correctIndex = correctIndex;
        }

        public String getText() { return text; }
        public List<String> getOptions() { return options; }
        public int getCorrectIndex() { return correctIndex; }
    }
}