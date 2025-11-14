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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
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
    private final Map<String, Integer> userBatchStart = new HashMap<>();

    private static class Question {
        private int id;
        private String question;
        private List<String> options;
        private int correctIndex;

        public Question() {
        }

        public int getId() {
            return id;
        }

        public String getQuestion() {
            return question;
        }

        public List<String> getOptions() {
            return options;
        }

        public int getCorrectIndex() {
            return correctIndex;
        }

        public void setId(int id) {
            this.id = id;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public void setOptions(List<String> options) {
            this.options = options;
        }

        public void setCorrectIndex(int correctIndex) {
            this.correctIndex = correctIndex;
        }
    }

    public MyBot(BotConfig botConfig, UserService userService, ResultService resultService, TestService testService) {
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
        specialtyQuestionsMap.forEach((key, value) -> System.out.println("✅ Loaded " + key + " questions count = " + value.size()));
    }

    private void loadSpecialtiesFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource("specialties.json");
            Type listType = new TypeToken<List<String>>() {
            }.getType();
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
            Type listType = new TypeToken<List<Question>>() {
            }.getType();
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
            // ✅ если пользователь прислал контакт (нажал "📲 Рақамингизни юборинг")
            if (update.hasMessage() && update.getMessage().hasContact()) {
                var msg = update.getMessage();
                String chatId = msg.getChatId().toString();
                String username = msg.getFrom().getUserName();
                String firstName = msg.getFrom().getFirstName();
                String phone = msg.getContact().getPhoneNumber();
                // сохраняем пользователя
                userService.ensureUser(chatId, username, firstName, phone);
                // ✅ убираем клавиатуру после получения контакта
                ReplyKeyboardRemove removeKeyboard = new ReplyKeyboardRemove(true);
                SendMessage confirmMsg = SendMessage.builder()
                        .chatId(chatId)
                        .text("✅ Рақамингиз сақланди: " + phone)
                        .replyMarkup(removeKeyboard)
                        .build();
                execute(confirmMsg);
                // ✅ теперь показываем меню
                sendStartMenu(chatId);
                return;
            }
            // ✅ если текстовое сообщение
            if (update.hasMessage() && update.getMessage().hasText()) {
                var msg = update.getMessage();
                String chatId = msg.getChatId().toString();
                String username = msg.getFrom().getUserName();
                String firstName = msg.getFrom().getFirstName();
                if (msg.getText().equals("/start")) {
                    // если пользователя нет — просим контакт
                    if (!userService.exists(chatId)) {
                        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
                        keyboard.setResizeKeyboard(true);
                        keyboard.setOneTimeKeyboard(false);
                        KeyboardButton contactButton = new KeyboardButton("📲 Рақамингизни юборинг");
                        contactButton.setRequestContact(true);
                        keyboard.setKeyboard(List.of(new KeyboardRow(List.of(contactButton))));
                        sendMessageWithReplyKeyboard(chatId, "Илтимос, рақамингизни юборинг:", keyboard);
                        return;
                    }
                    // иначе просто показываем меню
                    sendWelcome(chatId);
                    return;
                }
            }
            // ✅ callback query (нажатие inline-кнопок)
            if (update.hasCallbackQuery()) {
                var cq = update.getCallbackQuery();
                String chatId = cq.getMessage().getChatId().toString();
                String data = cq.getData();
                int msgId = cq.getMessage().getMessageId();

                // НОВАЯ ОБРАБОТКА: выбор блока (1-50, 51-100, ...)
                if (data.startsWith("block_")) {
                    handleBlockSelection(chatId, msgId, data);
                    return;
                }

                if (data.startsWith("spec_page_")) {
                    handleSpecialtyPageCallback(chatId, msgId, data);
                } else if (data.startsWith("spec_")) {
                    handleSpecialtySelection(chatId, msgId, data);
                } else if (data.equals("start_restart")) {
                    sendWelcome(chatId);
                } else if (data.equals("restart_test")) {
                    String spec = userSelectedSpecialty.get(chatId);
                    if (spec != null) {
                        String batchKey = chatId + "_" + spec;
                        userNextBatch.put(batchKey, 0);
                        startTest(chatId);
                    }
                } else if (data.equals("continue_test")) {
                    startTest(chatId);
                } else {
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
            }
            if (update.hasPollAnswer()) handlePollAnswer(update.getPollAnswer());
        } catch (Exception e) {
            e.printStackTrace();
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
                    InlineKeyboardButton.builder().text("📚 Сохалар").callbackData("list_specialties").build(),
                    InlineKeyboardButton.builder().text("📊 Менинг натижаларим").callbackData("my_results").build()
            ));
        } else {
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("🔒 Тўлов килиш").callbackData("pay_menu").build()
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
        String message = "💳 Обуна холати\n\n" + status;
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

    // ---------- Специальности ----------
    private void handleSpecialtiesListRequest(String chatId, int msgId) {
        if (!userService.canTakeTest(chatId)) {
            String status = userService.getAccessStatus(chatId);
            editMessage(chatId, msgId, "🔒 Сохаларга кириш ёпилган\n\n" + status + "\n\nТестларга кириш учун обунaни тўлаш керак.", InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(InlineKeyboardButton.builder().text("💰 Тўлаш").callbackData("pay_menu").build()))
                    .keyboardRow(List.of(InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()))
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
        editMessage(chatId, msgId, "📚 Сохани танланг (саҳ. " + (page + 1) + "/" + pages + "):", kb.build());
    }

    private void handleSpecialtyPageCallback(String chatId, int msgId, String data) {
        try {
            int p = Integer.parseInt(data.substring("spec_page_".length()));
            editSpecialtiesList(chatId, msgId, p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === НОВЫЕ МЕТОДЫ ===

    private void handleSpecialtySelection(String chatId, int msgId, String data) {
        try {
            int idx = Integer.parseInt(data.substring(5));
            if (idx >= 0 && idx < specialties.size()) {
                String spec = specialties.get(idx);
                userSelectedSpecialty.put(chatId, spec);
                showBlockSelectionMenu(chatId, msgId, spec);
            }
        } catch (Exception ignored) {}
    }

    private void showBlockSelectionMenu(String chatId, int msgId, String spec) {
        List<Question> allQuestions = specialtyQuestionsMap.get(spec);
        if (allQuestions == null || allQuestions.isEmpty()) {
            editMessage(chatId, msgId, "Саволлар топилмади.", null);
            return;
        }

        int totalQuestions = allQuestions.size();
        int blockSize = 50;
        int totalBlocks = (int) Math.ceil((double) totalQuestions / blockSize);

        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder kb = InlineKeyboardMarkup.builder();
        for (int i = 0; i < totalBlocks; i++) {
            int start = i * blockSize + 1;
            int end = Math.min((i + 1) * blockSize, totalQuestions);
            String buttonText = start + " - " + end;
            String callbackData = "block_" + i;
            kb.keyboardRow(List.of(
                    InlineKeyboardButton.builder()
                            .text(buttonText)
                            .callbackData(callbackData)
                            .build()
            ));
        }

        kb.keyboardRow(List.of(
                InlineKeyboardButton.builder().text("орқага").callbackData("list_specialties").build()
        ));

        editMessage(chatId, msgId,
                "\"" + spec + "\" учун блокни танланг (" + totalQuestions + " та умумий):",
                kb.build()
        );
    }

    private void handleBlockSelection(String chatId, int msgId, String data) {
        try {
            int blockIndex = Integer.parseInt(data.substring("block_".length()));
            String spec = userSelectedSpecialty.get(chatId);
            List<Question> allQuestions = specialtyQuestionsMap.get(spec);
            if (allQuestions == null) return;

            int blockSize = 50;
            int startIndex = blockIndex * blockSize;
            int endIndex = Math.min(startIndex + blockSize, allQuestions.size());
            int batchSize = endIndex - startIndex;

            if (startIndex >= allQuestions.size()) {
                editMessage(chatId, msgId, "Бу блок бўш.", null);
                return;
            }

            // Проверка на оплату: если блок > 50 и не оплачено
            if (!userService.canTakeTest(chatId) && startIndex >= 50) {
                editMessage(chatId, msgId,
                        "Бу блок фақат тўловдан сўнг мавжуд бўлади.\n\n" +
                                "Дастлабки 50 та савол — бепул.",
                        InlineKeyboardMarkup.builder()
                                .keyboardRow(List.of(
                                        InlineKeyboardButton.builder().text("тулов").callbackData("pay_menu").build(),
                                        InlineKeyboardButton.builder().text("орқага").callbackData("menu_main").build()
                                ))
                                .build()
                );
                return;
            }

            // Сохраняем блок
            userBatchStart.put(chatId, startIndex);
            userSpecialtyQuestions.put(chatId, new ArrayList<>(allQuestions.subList(startIndex, endIndex)));
            userCurrentQuestion.put(chatId, startIndex + 1);
            userScores.put(chatId, 0);

            // Сохраняем прогресс
            String batchKey = chatId + "_" + spec;
            userNextBatch.put(batchKey, startIndex);
            // 🔥 Обнуляем старый прогресс в БД

            userProgressService.saveProgress(chatId, 0, startIndex + 1, spec, startIndex);

            sendMessage(chatId, "Тест: саволлар " + (startIndex + 1) + " - " + endIndex + " умумий " + allQuestions.size() + " та саволдан.");
            sendSpecialtyQuestion(chatId, 1, batchSize);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    // ---------- Результаты и О проекте ----------
    private void editMyResults(String chatId, int msgId) {
        var results = resultService.getResults(chatId);
        if (results.isEmpty()) {
            editMessage(chatId, msgId, "📊 Сизда хали натижалар йўқ.", InlineKeyboardMarkup.builder()
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
        editMessage(chatId, msgId, sb.toString(), InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(InlineKeyboardButton.builder().text("⬅️ Орқага").callbackData("menu_main").build()))
                .build());
    }

    private void editAbout(String chatId, int msgId) {
        NumberFormat formatter = NumberFormat.getInstance(new Locale("ru", "RU"));
        String formattedPrice = formatter.format(botConfig.getSubscriptionPrice()).replace("\u00A0", ".");
        String aboutText = "ℹ️ Лойиха хақида\n\n" +
                "Тиббий тест бот - тиббий имтихонларга тайёргарлик платформаси.\n\n" +
                "💰 Нархи: " + formattedPrice + " сўм (бир марта тўлов)\n\n" +
                "✅ Тўловдан сўнг сиз оласиз:\n" +
                "• Барча тестларга чекланмаган кириш\n" +
                "• Барча тиббий ихтисослар\n" +
                "• Натижаларни сақлаш\n" +
                "• Абaдий\n\n";
        editMessage(chatId, msgId, aboutText, InlineKeyboardMarkup.builder()
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
            sendMessage(chatId, "🔒 Тестга кириш учун обуна талаб қилинади.");
            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("💰 Обунaни тўлаш").callbackData("pay_menu").build()
                    ))
                    .keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("⬅️ Менюга орқага").callbackData("menu_main").build()
                    ))
                    .build();
            sendMessage(chatId, "", markup);
            return;
        }
        String spec = userSelectedSpecialty.getOrDefault(chatId, "");
        List<Question> allQuestions = specialtyQuestionsMap.get(spec);
        if (allQuestions == null || allQuestions.isEmpty()) return;
        int totalQuestions = allQuestions.size();
        int blockSize = 50;
        String batchKey = chatId + "_" + spec;
        int startIndex = userNextBatch.getOrDefault(batchKey, 0);
        // Загружаем прогресс, если есть
        userProgressService.getProgress(chatId).ifPresent(progress -> {
            if (progress.getSelectedSpecialty().equals(spec)) {
                userScores.put(chatId, progress.getScore());
                userCurrentQuestion.put(chatId, progress.getCurrentQuestion());
                userNextBatch.put(batchKey, progress.getNextBatchIndex());
            }
        });
        if (startIndex >= totalQuestions) {
            sendMessage(chatId, "Барча саволлар тугатилган!");
            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("🔁 Бошдан бошлаш").callbackData("restart_test").build()
                    ))
                    .keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("⬅️ Менюга орқага").callbackData("menu_main").build()
                    ))
                    .build();
            sendMessage(chatId, "", markup);
            return;
        }
        int endIndex = Math.min(startIndex + blockSize, totalQuestions);
        int batchSize = endIndex - startIndex;
        userBatchStart.put(chatId, startIndex);
        userSpecialtyQuestions.put(chatId, new ArrayList<>(allQuestions.subList(startIndex, endIndex)));
        userCurrentQuestion.put(chatId, startIndex + 1); // 1-based global
        userScores.put(chatId, 0);
        sendMessage(chatId, "🧠 Тест бошланади: саволлар " + (startIndex + 1) + "–" + endIndex + " (" + totalQuestions + " тадан)");
        sendSpecialtyQuestion(chatId, 1, batchSize);
    }

    private void sendSpecialtyQuestion(String chatId, int qNumber, int total) {
        List<Question> qs = userSpecialtyQuestions.get(chatId);
        int batchStart = userBatchStart.getOrDefault(chatId, 0);
        if (qs == null || qNumber > total) {
            int score = userScores.getOrDefault(chatId, 0);
            // Сохраняем результат
            resultService.saveResult(chatId, score, total);
            // Обновляем старт следующего блока
            int nextStart = batchStart + total;
            String spec = userSelectedSpecialty.get(chatId);
            String batchKey = chatId + "_" + spec;
            userNextBatch.put(batchKey, nextStart);
            int totalQuestions = specialtyQuestionsMap.getOrDefault(spec, Collections.emptyList()).size();
            String message = "🎉 Блок тугатилди!\nСизнинг натижангиз: " + score + " дан " + total;
            InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup.builder();
            builder.keyboardRow(List.of(
                    InlineKeyboardButton.builder().text("⬅️ Менюга орқага").callbackData("menu_main").build()
            ));
            boolean allDone = nextStart >= totalQuestions;
            if (allDone) {
                message = "🎉 Барча тестлар тугатилди!\nОхирги блок учун натижангиз: " + score + " дан " + total;
                builder.keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("🔁 Бошдан бошлаш").callbackData("restart_test").build()
                ));
            } else {
                if (userService.canTakeTest(chatId)) {
                    builder.keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("➡️ Кейинги блок").callbackData("continue_test").build()
                    ));
                } else {
                    message += "\n\nКейинги блок учун обуна талаб қилинади!";
                    builder.keyboardRow(List.of(
                            InlineKeyboardButton.builder().text("💰 Обунaни тўлаш").callbackData("pay_menu").build()
                    ));
                }
            }
            sendMessage(chatId, message, builder.build());
            // Убираем текущий блок вопросов
            userSpecialtyQuestions.remove(chatId);
            userBatchStart.remove(chatId);
            return;
        }
        // Берём вопрос из блока
        Question q = qs.get(qNumber - 1);
        // **Глобальный индекс = старт блока + позиция в блоке**
        int globalIndex = batchStart + (qNumber - 1);
        String questionText = "[" + (globalIndex + 1) + "/" + specialtyQuestionsMap.get(userSelectedSpecialty.get(chatId)).size() + "] " + q.getId() + ". " + q.getQuestion();
        if (questionText.length() > 300) questionText = questionText.substring(0, 297) + "...";
        List<String> options = new ArrayList<>();
        for (String opt : q.getOptions()) {
            if (opt != null && !opt.isBlank()) {
                options.add(opt.length() > 100 ? opt.substring(0, 97) + "..." : opt);
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
        // Увеличиваем счетчик решённых вопросов
        if (!userService.getUser(chatId).map(UserEntity::getIsPaid).orElse(false)) {
            userService.increaseSolvedCount(chatId, 1);
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
        String spec = userSelectedSpecialty.getOrDefault(chatId, "");
        String batchKey = chatId + "_" + spec;
        // Загружаем прогресс пользователя
        userProgressService.getProgress(chatId).ifPresent(progress -> {
            userScores.put(chatId, progress.getScore());
            userCurrentQuestion.put(chatId, progress.getCurrentQuestion());
            userSelectedSpecialty.put(chatId, progress.getSelectedSpecialty());
            userNextBatch.put(batchKey, progress.getNextBatchIndex());
        });
        // Восстанавливаем batch если нужно (после рестарта)
        if (!userSpecialtyQuestions.containsKey(chatId) && specialtyQuestionsMap.containsKey(spec)) {
            List<Question> all = specialtyQuestionsMap.get(spec);
            int blockSize = 50;
            int globalNext = userCurrentQuestion.getOrDefault(chatId, 1);
            int start = ((globalNext - 1) / blockSize) * blockSize;
            int end = Math.min(start + blockSize, all.size());
            userBatchStart.put(chatId, start);
            userSpecialtyQuestions.put(chatId, all.subList(start, end));
            userNextBatch.put(batchKey, start);
        }
        // Проверяем, есть ли вопросы для этой специальности
        if (specialtyQuestionsMap.containsKey(spec)) {
            List<Question> qs = userSpecialtyQuestions.get(chatId);
            int batchStart = userBatchStart.getOrDefault(chatId, 0);
            int globalQ = userCurrentQuestion.getOrDefault(chatId, 1);
            int localQ = globalQ - batchStart;
            if (qs == null || localQ > qs.size() || localQ <= 0) return;
            Question q = qs.get(localQ - 1);
            if (selected == q.getCorrectIndex()) {
                userScores.put(chatId, userScores.getOrDefault(chatId, 0) + 1);
            }
            int nextGlobal = globalQ + 1;
            userCurrentQuestion.put(chatId, nextGlobal);
            int batchSize = qs.size();
            int nextLocal = nextGlobal - batchStart;
            // Сохраняем прогресс пользователя
            userProgressService.saveProgress(chatId, userScores.get(chatId), nextGlobal, spec, userNextBatch.getOrDefault(batchKey, 0));
            // Отправляем следующий вопрос через 5 секунд
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    sendSpecialtyQuestion(chatId, nextLocal, batchSize);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            // Старая логика для тестов без JSON
            var optQ = testService.getQuestion(userCurrentQuestion.getOrDefault(chatId, 1));
            if (optQ.isEmpty()) return;
            var q = optQ.get();
            if (selected == q.getCorrectIndex()) userScores.put(chatId, userScores.getOrDefault(chatId, 0) + 1);
            int next = userCurrentQuestion.getOrDefault(chatId, 1) + 1;
            userCurrentQuestion.put(chatId, next);
            if (next > testService.totalQuestions()) {
                int score = userScores.getOrDefault(chatId, 0);
                resultService.saveResult(chatId, score, testService.totalQuestions());
                InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(
                                InlineKeyboardButton.builder().text("⬅️ Менюга орқага").callbackData("menu_main").build(),
                                InlineKeyboardButton.builder().text("🔁 Яна ўтиш").callbackData("start_test").build()
                        ))
                        .build();
                sendMessage(chatId, "🎉 Тест тугатилди!\nСизнинг натижангиз: " + score + " дан " + testService.totalQuestions(), markup);
                return; // ✅ не вызываем sendStartMenu
            }
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
                "Илтимос, «Старт» тугмасини босинг.";
        // Просто вызываем метод, не оборачивая в try/catch
        sendMessage(chatId, text, markup);
    }

    private void sendMessageWithReplyKeyboard(String chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public String getBotUsername() {
        return botConfig.getUsername();
    }
}