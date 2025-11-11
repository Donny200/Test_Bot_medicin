package uz.pdp.test_bot.util;

import com.google.gson.Gson;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.lang.reflect.Type;

public class JsonLoader {

    public static <T> T loadFromJson(String path, Type type) {
        try {
            var resource = new ClassPathResource(path);
            try (var reader = new InputStreamReader(resource.getInputStream(), "UTF-8")) {
                return new Gson().fromJson(reader, type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
