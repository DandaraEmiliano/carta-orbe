package br.dev.cartaorbe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
class DemoLetters {
    record Example(String name, String resume, String vacancy) {}

    private final Example example;
    private final Map<Letter.Language, Letter.Result> letters = new EnumMap<>(Letter.Language.class);

    DemoLetters(ObjectMapper mapper) throws IOException {
        example = new Example("Marina Costa", resource("resume.txt"), resource("vacancy.txt"));
        for (Letter.Language language : Letter.Language.values()) {
            Letter.Request request = new Letter.Request(example.name(), example.resume(), example.vacancy(),
                    language, Letter.Tone.PROFESSIONAL, "");
            Letter.Draft draft = mapper.readValue(resource(language.name().toLowerCase(java.util.Locale.ROOT) + ".json"),
                    Letter.Draft.class);
            letters.put(language, Letter.format(request, draft, true));
        }
    }

    Example example() {
        return example;
    }

    Letter.Result letter(Letter.Language language) {
        return letters.get(language);
    }

    private static String resource(String name) throws IOException {
        return new ClassPathResource("examples/" + name).getContentAsString(StandardCharsets.UTF_8).strip();
    }
}
