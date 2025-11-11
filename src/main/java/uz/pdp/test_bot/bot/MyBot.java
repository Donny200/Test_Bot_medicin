package uz.pdp.test_bot.bot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.polls.SendPoll;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.polls.PollAnswer;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.pdp.test_bot.config.BotConfig;
import uz.pdp.test_bot.entity.UserEntity;
import uz.pdp.test_bot.repository.UserRepository;
import uz.pdp.test_bot.service.ResultService;
import uz.pdp.test_bot.service.TestService;
import uz.pdp.test_bot.service.UserProgressService;
import uz.pdp.test_bot.service.UserService;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Getter
@Setter
public class MyBot extends TelegramLongPollingBot {
    private final BotConfig botConfig;
    private final UserService userService;
    private final ResultService resultService;
    private final TestService testService;
    private UserRepository userRepository;
    private final Map<String, Integer> userScores = new HashMap<>();
    private final Map<String, Integer> userCurrentQuestion = new HashMap<>();
    private final Map<String, String> userSelectedSpecialty = new HashMap<>();
    private final Map<String, List<Question>> userSpecialtyQuestions = new HashMap<>();
    private final List<String> specialties = new ArrayList<>();
    @Autowired
    private UserProgressService userProgressService;


    // Храним вопросы для каждой специальности
    private final Map<String, List<Question>> specialtyQuestionsMap = new HashMap<>();

    private final Gson gson = new Gson();
    private final Map<String, Integer> userNextBatch = new HashMap<>();

    private static class Question {
        private int id;
        private String question;
        private List<String> options;
        private int correctIndex;
        public Question() {}
        public int getId() { return id; }
        public String getQuestion() { return question; }
        public List<String> getOptions() { return options; }
        public int getCorrectIndex() { return correctIndex; }
        public void setId(int id) { this.id = id; }
        public void setQuestion(String question) { this.question = question; }
        public void setOptions(List<String> options) { this.options = options; }
        public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }
    }

    public MyBot(BotConfig botConfig,
                 UserService userService,
                 ResultService resultService,
                 TestService testService) {
        this.botConfig = botConfig;
        this.userService = userService;
        this.resultService = resultService;
        this.testService = testService;
        this.userProgressService = userProgressService; // ✅ здесь инициализация
    }

    @PostConstruct
    public void init() {
        loadSpecialtiesFromJson();
        loadSpecialtyQuestions("oilaviy_shifokorlik");
        loadSpecialtyQuestions("pediatria");
        loadSpecialtyQuestions("oftalmologiya");

        System.out.println("✅ MyBot initialized with username = " + getBotUsername());
        System.out.println("✅ Loaded specialties count = " + specialties.size());
        specialtyQuestionsMap.forEach((key, value) ->
                System.out.println("✅ Loaded " + key + " questions count = " + value.size())
        );
    }

    private void loadSpecialtiesFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource("specialties.json");
            Type listType = new TypeToken<List<String>>() {}.getType();
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), "UTF-8")) {
                List<String> list = gson.fromJson(reader, listType);
                if (list != null) specialties.addAll(list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSpecialtyQuestions(String specialty) {
        try {
            ClassPathResource resource = new ClassPathResource("specialties/" + specialty + ".json");
            Type listType = new TypeToken<List<Question>>() {}.getType();
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), "UTF-8")) {
                List<Question> list = gson.fromJson(reader, listType);
                if (list != null && !list.isEmpty()) {
                    specialtyQuestionsMap.put(specialty, new ArrayList<>(list));
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not load questions for " + specialty + ": " + e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                var msg = update.getMessage();
                String chatId = msg.getChatId().toString();
                String username = msg.getFrom().getUserName();
                String firstName = msg.getFrom().getFirstName();
                String phone = null;

                if (msg.hasContact()) {
                    phone = msg.getContact().getPhoneNumber();
                }

                userService.ensureUser(chatId, username, firstName, phone);

                if (msg.getText().equals("/start")) {
                    sendWelcome(chatId);
                    return;
                }
            }
            if (update.hasCallbackQuery()) {
                var cq = update.getCallbackQuery();
                String data = cq.getData();
                String chatId = cq.getMessage().getChatId().toString();
                Integer msgId = cq.getMessage().getMessageId();
                if (data.startsWith("spec_page_")) {
                    handleSpecialtyPageCallback(chatId, msgId, data);
                    return;
                }
                if (data.startsWith("spec_")) {
                    handleSpecialtySelection(chatId, msgId, data);
                    return;
                }
                if (data.equals("simulate_payment")) {
                    handleSimulatePayment(chatId, msgId);
                    return;
                }
                if (data.equals("start_restart")) {
                    sendWelcome(chatId);
                    return;
                }
                switch (data) {
                    case "menu_main" -> editStartMenu(chatId, msgId);
                    case "list_specialties" -> handleSpecialtiesListRequest(chatId, msgId);
                    case "my_results" -> editMyResults(chatId, msgId);
                    case "about" -> editAbout(chatId, msgId);
                    case "my_subscription" -> editSubscriptionStatus(chatId, msgId);
                    case "pay_menu" -> handlePaymentInfo(chatId, msgId);
                    case "start_test" -> startTest(chatId);
                    default -> sendMessage(chatId, "Номаълум буйруқ: " + data);
                }
            }
            if (update.hasPollAnswer()) handlePollAnswer(update.getPollAnswer());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---------- Меню ----------
    private void sendWelcome(String chatId) {
        String status = userService.getAccessStatus(chatId);
        sendMessage(chatId, "🩺 Тиббий тест ботга хуш келибсиз!\n\n" + status);
        sendStartMenu(chatId);
    }

    private InlineKeyboardMarkup getMainMenu(String chatId) {
        boolean canTest = userService.canTakeTest(chatId);
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder kb = InlineKeyboardMarkup.builder();
        if (canTest) {
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("📚 Ихтисослар").callbackData("list_specialties").build(),
                    InlineKeyboardButton.builder().text("📊 Менинг натижаларим").callbackData("my_results").build()
            ));
        } else {
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("🔒 Ихтисослар (тўлов талаб қилинади)").callbackData("pay_menu").build()
            ));
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("📊 Менинг натижаларим").callbackData("my_results").build()
            ));
        }
        kb.keyboardRow(List.of(
                InlineKeyboardButton.builder().text("💳 Менинг обунaм").callbackData("my_subscription").build(),
                InlineKeyboardButton.builder().text("ℹ️ Лойиҳа ҳақида").callbackData("about").build()
        ));
        return kb.build();
    }

    private void sendStartMenu(String chatId) {
        sendMessage(chatId, "📋 Асосий меню:", getMainMenu(chatId));
    }

    private void editStartMenu(String chatId, int msgId) {
        editMessage(chatId, msgId, "📋 Асосий меню:", getMainMenu(chatId));
    }

    // ---------- Подписка и оплата ----------
    private void editSubscriptionStatus(String chatId, int msgId) {
        String status = userService.getAccessStatus(chatId);
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder kb = InlineKeyboardMarkup.builder();
        if (!userService.canTakeTest(chatId)) {
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("💰 Обунaни тўлаш").callbackData("pay_menu").build()
            ));
        }
        kb.keyboardRow(List.of(
                InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()
        ));
        String message = "💳 Обуна ҳолати\n\n" + status;
        if (!userService.canTakeTest(chatId)) {
            NumberFormat formatter = NumberFormat.getInstance(new Locale("ru", "RU"));
            String formattedPrice = formatter.format(botConfig.getSubscriptionPrice()).replace("\u00A0", ".");
            message += "\n\n💰 Обуна нархи: " + formattedPrice + " сўм";
            message += "\n\n✅ Тўловдан сўнг сиз қўлингизга ўтади:\n" +
                    "• Тестларга чекланмаган кириш\n" +
                    "• Барча ихтисослар\n" +
                    "• Натижаларни сақлаш\n" +
                    "• Абaдий (бир марта тўлов)";
        }
        editMessage(chatId, msgId, message, kb.build());
    }

    private void handlePaymentInfo(String chatId, int msgId) {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("ru", "RU"));
        String formattedPrice = formatter.format(botConfig.getSubscriptionPrice()).replace("\u00A0", ".");
        String message = "💰 Сумма: " + formattedPrice + " сўм\n\n" +
                "1. Пулни кartaга ўтказинг:\n" +
                " • Кarta рақами: " + botConfig.getCardNumber() + "\n" +
                " • Эгаси: " + botConfig.getCardOwner() + "\n\n" +
                "2. Ўтказгандан сўнг чек скриншотини администраторга шахсий хабарлар орқали юборг:\n" +
                " " + botConfig.getTelegramUsername() + "\n\n" +
                "Администратор тўловни текшириб, киришни қўлда фaоллаштиради.\n\n";
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()
                ))
                .build();
        editMessage(chatId, msgId, message, markup);
    }

    private void handleSimulatePayment(String chatId, int msgId) {
        UserEntity user = userService.getUser(chatId).orElse(null);
        if (user != null) {
            user.setFirstTestDate(LocalDateTime.now().minusHours(1));
            userRepository.save(user);
        }
        String message = "✅ Тўлов симуляцияси бажарилди!\n" +
                "🎉 Энди сиз тестларни ўтиб бўласиз.\n\n" +
                "💳 Ҳақиқий isPaid ҳали ҳам йўқ.";
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("📚 Тестларга ўтиш").callbackData("list_specialties").build()
                ))
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("🏠 Асосий меню").callbackData("menu_main").build()
                ))
                .build();
        editMessage(chatId, msgId, message, markup);
    }

    // ---------- Специальности ----------
    private void handleSpecialtiesListRequest(String chatId, int msgId) {
        if (!userService.canTakeTest(chatId)) {
            String status = userService.getAccessStatus(chatId);
            editMessage(chatId, msgId,
                    "🔒 Ихтисосларга кириш ёпилган\n\n" +
                            status +
                            "\n\nТестларга кириш учун обунaни тўлаш ва скриншотни администраторга юборг.",
                    InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(
                                    InlineKeyboardButton.builder().text("💰 Тўлаш").callbackData("pay_menu").build()
                            ))
                            .keyboardRow(List.of(
                                    InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()
                            ))
                            .build()
            );
            return;
        }
        editSpecialtiesList(chatId, msgId, 0);
    }

    private void editSpecialtiesList(String chatId, int msgId, int page) {
        int pageSize = 8;
        int total = specialties.size();
        int pages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * pageSize;
        int end = Math.min(total, start + pageSize);
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder kb = InlineKeyboardMarkup.builder();
        for (int i = start; i < end; i++) {
            String spec = specialties.get(i);
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text(spec).callbackData("spec_" + i).build()
            ));
        }
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) nav.add(InlineKeyboardButton.builder().text("⬅️").callbackData("spec_page_" + (page - 1)).build());
        if (page < pages - 1) nav.add(InlineKeyboardButton.builder().text("➡️").callbackData("spec_page_" + (page + 1)).build());
        if (!nav.isEmpty()) kb.keyboardRow(nav);
        kb.keyboardRow(List.of(InlineKeyboardButton.builder().text("🏠 Асосий меню").callbackData("menu_main").build()));
        editMessage(chatId, msgId, "📚 Ихтисосни танланг (саҳ. " + (page + 1) + "/" + pages + "):", kb.build());
    }

    private void handleSpecialtyPageCallback(String chatId, int msgId, String data) {
        try {
            int p = Integer.parseInt(data.substring("spec_page_".length()));
            editSpecialtiesList(chatId, msgId, p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleSpecialtySelection(String chatId, int msgId, String data) {
        try {
            int idx = Integer.parseInt(data.substring(5));
            if (idx >= 0 && idx < specialties.size()) {
                String spec = specialties.get(idx);
                userSelectedSpecialty.put(chatId, spec);
                InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(
                                InlineKeyboardButton.builder().text("🧠 " + spec + " бўйича тестни бошлаш").callbackData("start_test").build()
                        ))
                        .keyboardRow(List.of(
                                InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("list_specialties").build()
                        ))
                        .build();
                editMessage(chatId, msgId, "Сиз танладингиз: " + spec + "\n\nТестни бошлаш учун босинг:", markup);
            }
        } catch (Exception ignored) {}
    }

    // ---------- Результаты и О проекте ----------
    private void editMyResults(String chatId, int msgId) {
        var results = resultService.getResults(chatId);
        if (results.isEmpty()) {
            editMessage(chatId, msgId, "📊 Сизда ҳали натижалар йўқ.",
                    InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()))
                            .build());
            return;
        }
        StringBuilder sb = new StringBuilder("📚 Сизнинг натижаларингиз:\n\n");
        var formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm")
                .withLocale(new java.util.Locale("ru"));
        for (var r : results) {
            sb.append("🗓 ").append(r.getCreatedAt().format(formatter))
                    .append("\nНатижа: ").append(r.getScore()).append("/").append(r.getTotal()).append("\n\n");
        }
        editMessage(chatId, msgId, sb.toString(),
                InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()))
                        .build());
    }

    private void editAbout(String chatId, int msgId) {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("ru", "RU"));
        String formattedPrice = formatter.format(botConfig.getSubscriptionPrice()).replace("\u00A0", ".");
        String aboutText = "ℹ️ Лойиҳа ҳақида\n\n" +
                "Тиббий тест бот - тиббий имтиҳонларга тайёргарлик платформаси.\n\n" +
                "💰 Нархи: " + formattedPrice + " сўм (бир марта тўлов)\n\n" +
                "✅ Тўловдан сўнг сиз оласиз:\n" +
                "• Барча тестларга чекланмаган кириш\n" +
                "• Барча тиббий ихтисослар\n" +
                "• Натижаларни сақлаш\n" +
                "• Абaдий\n\n" +
                "🎁 Бепул давр: биринчи тест ўтгазгандан кейин 24 соат";
        editMessage(chatId, msgId, aboutText,
                InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(
                                InlineKeyboardButton.builder()
                                        .text("⬅️ Орқага")
                                        .callbackData("menu_main")
                                        .build()
                        ))
                        .build());
    }

    // ---------- Тест ----------
    private void startTest(String chatId) {
        if (!userService.canTakeTest(chatId)) {
            String status = userService.getAccessStatus(chatId);
            sendMessage(chatId, "🔒 Тестларга кириш ёпилган.\n\n" +
                            status +
                            "\n\nТестларга кириш учун обунaни тўлаш ва скриншотни администраторга юборг.",
                    InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(
                                    InlineKeyboardButton.builder().text("💰 Тўлаш").callbackData("pay_menu").build()
                            ))
                            .keyboardRow(List.of(
                                    InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()
                            ))
                            .build()
            );
            return;
        }

        String spec = userSelectedSpecialty.getOrDefault(chatId, "");

        // Проверяем, есть ли вопросы для этой специальности
        List<Question> allQuestions = specialtyQuestionsMap.get(spec);

        if (allQuestions != null && !allQuestions.isEmpty()) {
            // Используем единую логику для всех специальностей с вопросами из JSON
            int totalQuestions = allQuestions.size();
            int blockSize = 50;

            // Получаем текущий индекс начала блока для этой специальности
            String batchKey = chatId + "_" + spec;
            int startIndex = userNextBatch.getOrDefault(batchKey, 0);

            // Если дошли до конца, начинаем сначала
            // Если дошли до конца всех вопросов JSON, начинаем сначала
            if (startIndex >= totalQuestions) {
                startIndex = 0;
                userNextBatch.put(batchKey, 0);
                userScores.put(chatId, 0);         // сброс баллов
                userCurrentQuestion.put(chatId, 1); // сброс номера вопроса
            }


            // Вычисляем конечный индекс блока
            int endIndex = Math.min(startIndex + blockSize, totalQuestions);

            // Информируем пользователя о диапазоне вопросов
            sendMessage(chatId, "🧠 Тест бошланади: саволлар " + (startIndex + 1) + "–" + endIndex + " (" + totalQuestions + " тадан)");

            // Извлекаем подмножество вопросов для текущего блока
            List<Question> selected = new ArrayList<>(allQuestions.subList(startIndex, endIndex));

            // Сохраняем начало следующего блока
            userNextBatch.put(batchKey, endIndex);

            // Сохраняем текущий блок вопросов для пользователя
            userSpecialtyQuestions.put(chatId, selected);

            // Инициализируем счетчик баллов и номер текущего вопроса
            userScores.put(chatId, 0);
            userCurrentQuestion.put(chatId, 1);

            // Отправляем первый вопрос
            sendSpecialtyQuestion(chatId, 1, selected.size());
        } else {
            // Используем старую логику для специальностей без JSON
            userScores.put(chatId, 0);
            userCurrentQuestion.put(chatId, 1);
            sendQuestion(chatId, 1);
        }
    }

    private void sendSpecialtyQuestion(String chatId, int qNumber, int total) {
        List<Question> qs = userSpecialtyQuestions.get(chatId);
        if (qs == null || qNumber > total) {
            int score = userScores.getOrDefault(chatId, 0);
            resultService.saveResult(chatId, score, total);
            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("⬅️ Менюга орқага").callbackData("menu_main").build(),
                            InlineKeyboardButton.builder().text("🔁 Яна ўтиш").callbackData("start_test").build()
                    ))
                    .build();
            sendMessage(chatId, "🎉 Тест тугатилди!\nСизнинг натижангиз: " + score + " дан " + total, markup);
            userSpecialtyQuestions.remove(chatId);
            return;
        }
        Question q = qs.get(qNumber - 1);
        String questionText = "[" + qNumber + "/" + total + "] " + q.getId() + ". " + q.getQuestion();
        if (questionText.length() > 300) questionText = questionText.substring(0, 297) + "...";
        List<String> options = new ArrayList<>();
        for (String opt : q.getOptions()) {
            if (opt != null && !opt.isBlank()) {
                String trimmed = opt.length() > 100 ? opt.substring(0, 97) + "..." : opt;
                options.add(trimmed);
            }
            if (options.size() >= 10) break;
        }
        if (options.size() < 2) {
            sendSpecialtyQuestion(chatId, qNumber + 1, total);
            return;
        }
        int correctIndex = q.getCorrectIndex();
        if (correctIndex >= options.size()) correctIndex = 0;
        SendPoll poll = SendPoll.builder()
                .chatId(chatId)
                .question(questionText)
                .options(options)
                .type("quiz")
                .isAnonymous(false)
                .correctOptionId(correctIndex)
                .build();
        try {
            execute(poll);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendSpecialtyQuestion(chatId, qNumber + 1, total);
        }
    }

    private void sendQuestion(String chatId, int qNumber) {
        var optQ = testService.getQuestion(qNumber);
        if (optQ.isEmpty()) {
            int score = userScores.getOrDefault(chatId, 0);
            int total = testService.totalQuestions();
            resultService.saveResult(chatId, score, total);
            sendMessage(chatId, "🎉 Тест тугатилди!\nСизнинг натижангиз: " + score + " дан " + total);
            sendStartMenu(chatId);
            return;
        }
        var q = optQ.get();
        SendPoll poll = SendPoll.builder()
                .chatId(chatId)
                .question(q.getText())
                .options(q.getOptions())
                .type("quiz")
                .isAnonymous(false)
                .correctOptionId(q.getCorrectIndex())
                .build();
        try {
            execute(poll);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handlePollAnswer(PollAnswer answer) {
        String chatId = String.valueOf(answer.getUser().getId());
        int selected = answer.getOptionIds().get(0);
        int qNumber = userCurrentQuestion.getOrDefault(chatId, 1);
        String spec = userSelectedSpecialty.getOrDefault(chatId, "");

// ✅ Загружаем прогресс пользователя
        userProgressService.getProgress(chatId).ifPresent(progress -> {
            userScores.put(chatId, progress.getScore());
            userCurrentQuestion.put(chatId, progress.getCurrentQuestion());
            userSelectedSpecialty.put(chatId, progress.getSelectedSpecialty());
            userNextBatch.put(chatId + "_" + spec, progress.getNextBatchIndex()); // <-- важно
        });


        // Проверяем, есть ли вопросы для этой специальности
        if (specialtyQuestionsMap.containsKey(spec)) {
            List<Question> qs = userSpecialtyQuestions.get(chatId);
            if (qs == null || qNumber > qs.size()) return;

            Question q = qs.get(qNumber - 1);
            if (selected == q.getCorrectIndex()) {
                userScores.put(chatId, userScores.getOrDefault(chatId, 0) + 1);
            }

            int total = qs.size();
            int next = qNumber + 1;
            userCurrentQuestion.put(chatId, next);
            // ✅ Сохраняем прогресс пользователя
            // Сохраняем прогресс
            userProgressService.saveProgress(chatId,
                    userScores.get(chatId),
                    userCurrentQuestion.get(chatId),
                    spec,
                    userNextBatch.getOrDefault(chatId + "_" + spec, 0));



            if (next > total) {
                int score = userScores.getOrDefault(chatId, 0);
                resultService.saveResult(chatId, score, total);
                sendMessage(chatId, "🎉 Тест тугатилди!\nСизнинг натижангиз: " + score + " дан " + total);
                userSpecialtyQuestions.remove(chatId);
                sendStartMenu(chatId);
                return;
            }

            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    sendSpecialtyQuestion(chatId, next, total);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            // Старая логика для специальностей без JSON
            var optQ = testService.getQuestion(qNumber);
            if (optQ.isEmpty()) return;
            var q = optQ.get();
            if (selected == q.getCorrectIndex())
                userScores.put(chatId, userScores.getOrDefault(chatId, 0) + 1);
            int next = qNumber + 1;
            userCurrentQuestion.put(chatId, next);
            if (next > testService.totalQuestions()) {
                int score = userScores.getOrDefault(chatId, 0);
                resultService.saveResult(chatId, score, testService.totalQuestions());
                sendMessage(chatId, "🎉 Тест тугатилди!\nСизнинг натижангиз: " + score + " дан " + testService.totalQuestions());
                sendStartMenu(chatId);
            } else {
                new Thread(() -> {
                    try {
                        Thread.sleep(30000);
                        sendQuestion(chatId, next);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }
    }

    // ---------- Общие методы ----------
    private void sendMessage(String chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessage(String chatId, String text, InlineKeyboardMarkup markup) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(markup).build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void editMessage(String chatId, int msgId, String text, InlineKeyboardMarkup markup) {
        try {
            execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(msgId)
                    .text(text)
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ---------- Рассылка при обновлении ----------
    @PostConstruct
    public void notifyAllUsersAfterRestart() {
        new Thread(() -> {
            try {
                Thread.sleep(8000); // ждём 8 секунд, чтобы бот полностью запустился
                List<UserEntity> users = userService.getAllUsers();
                for (UserEntity user : users) {
                    sendRestartMessage(user.getChatId());
                }
                System.out.println("✅ Сообщение об обновлении отправлено всем пользователям (" + users.size() + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendRestartMessage(String chatId) {
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("▶️ Старт")
                                .callbackData("start_restart")
                                .build()
                ))
                .build();

        String text = "⚙️ Бот янгиланди!\n\n" +
                "Илтимос, «Старт» тугмасини босинг, фойдаланишни давом эттириш учун.";
        sendMessage(chatId, text, markup);
    }


    @Override
    public String getBotToken() { return botConfig.getToken(); }

    @Override
    public String getBotUsername() { return botConfig.getUsername(); }
}