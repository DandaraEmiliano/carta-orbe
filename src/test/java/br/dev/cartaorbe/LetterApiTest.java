package br.dev.cartaorbe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "orbe.model.enabled=false")
@AutoConfigureMockMvc
class LetterApiTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;

    @ParameterizedTest
    @EnumSource(Letter.Language.class)
    void servesFormattedExamplesInThreeLanguages(Letter.Language language) throws Exception {
        http.perform(get("/api/examples/" + language))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text", startsWith(language.greeting)))
                .andExpect(jsonPath("$.wordCount", both(greaterThanOrEqualTo(250)).and(lessThanOrEqualTo(380))))
                .andExpect(jsonPath("$.evidence", hasSize(3)))
                .andExpect(jsonPath("$.example").value(true));
    }

    @Test
    void extractsLatexWithoutCompilingIt() throws Exception {
        String cv = """
                \\documentclass{article}
                \\begin{document}
                \\section{Experiência}
                \\textbf{Java} e Spring Boot para rastrear entregas.
                % comentário interno
                Reduzi a latência em 20\\%.
                \\end{document}
                """;
        var file = new MockMultipartFile("file", "curriculo.tex", "text/plain", cv.getBytes(UTF_8));
        http.perform(multipart("/api/resume").file(file))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.text", containsString("Java e Spring Boot")))
                .andExpect(jsonPath("$.text", containsString("20%")))
                .andExpect(jsonPath("$.text", not(containsString("comentário interno"))))
                .andExpect(jsonPath("$.text", not(containsString("documentclass"))));
    }

    @Test
    void rejectsAnUnsupportedVacancyHost() throws Exception {
        http.perform(post("/api/vacancy").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://exemplo.test/vaga\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reportsThatCustomGenerationIsDisabledInTheDemo() throws Exception {
        String input = mapper.writeValueAsString(Map.of("name", "Marina Costa", "language", "ES",
                "resume", "Experiência em Java, Spring Boot e relatórios de desempenho.",
                "vacancy", "Busca-se desenvolvedora com experiência em Java e Spring Boot."));
        http.perform(post("/api/letters").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isServiceUnavailable());
    }
}
