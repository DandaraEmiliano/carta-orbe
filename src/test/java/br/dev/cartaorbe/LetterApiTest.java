package br.dev.cartaorbe;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
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
import static org.assertj.core.api.Assertions.assertThat;
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
    void exportsOnePageAndExtractsAccentedTextBackIntoTheForm() throws Exception {
        String letter = """
                Estimado equipo de selección:

                Mi experiencia en ingeniería incluye el diseño de APIs de seguimiento de entregas.
                También escribí pruebas de integración y documentación para la recuperación.

                Atentamente,
                João García
                """;
        byte[] pdf = http.perform(post("/api/export/pdf").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("text", letter))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"carta-orbe.pdf\""))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("selección", "João García");
        }
        var upload = new MockMultipartFile("file", "curriculo.pdf", "application/pdf", pdf);
        http.perform(multipart("/api/resume").file(upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdf").value(true))
                .andExpect(jsonPath("$.text", containsString("João García")));
    }

    @Test
    void suggestsOcrForAPdfWithoutSelectableText() throws Exception {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            var file = new MockMultipartFile("file", "digitalizado.pdf", "application/pdf", bytes.toByteArray());
            http.perform(multipart("/api/resume").file(file))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail", containsString("OCR")));
        }
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
