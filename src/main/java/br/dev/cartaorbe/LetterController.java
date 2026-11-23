package br.dev.cartaorbe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api")
class LetterController {
    private final OllamaLetterWriter writer;
    private final DemoLetters demo;
    private final VacancyImport vacancies;
    private final PdfDocuments pdf;

    LetterController(OllamaLetterWriter writer, DemoLetters demo, VacancyImport vacancies, PdfDocuments pdf) {
        this.writer = writer;
        this.demo = demo;
        this.vacancies = vacancies;
        this.pdf = pdf;
    }

    @GetMapping("/settings")
    Map<String, Boolean> settings() {
        return Map.of("customGeneration", writer.enabled(), "requiresCode", writer.requiresCode(),
                "remoteModel", writer.remote());
    }

    @GetMapping("/examples")
    DemoLetters.Example example() {
        return demo.example();
    }

    @GetMapping("/examples/{language}")
    Letter.Result exampleLetter(@PathVariable Letter.Language language) {
        return demo.letter(language);
    }

    record Resume(String text, boolean pdf) {}

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Resume> resume(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return privateResponse(new Resume(pdf.extract(file.getBytes()), true));
        }
        if (!(name.endsWith(".tex") || name.endsWith(".txt"))) {
            throw bad("Escolha um arquivo .pdf, .txt ou .tex.");
        }
        if (file.getSize() > 64 * 1024) {
            throw new ApiProblem(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivos .txt e .tex devem ter até 64 KB.");
        }
        try {
            String decoded = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();
            if (decoded.indexOf('\0') >= 0) {
                throw bad("O arquivo deve conter texto em UTF-8.");
            }
            String clean = text(ResumeText.normalize(decoded, name.endsWith(".tex")), 40, 12000, "currículo");
            return privateResponse(new Resume(clean, false));
        } catch (CharacterCodingException invalid) {
            throw bad("O arquivo deve conter texto em UTF-8.");
        }
    }

    record Export(String text) {}

    @PostMapping(value = "/export/pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> export(@RequestBody Export input) throws IOException {
        String content = Normalizer.normalize(text(input.text(), 1, 6000, "texto da carta"), Normalizer.Form.NFC);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("carta-orbe.pdf").build().toString())
                .body(pdf.letter(content));
    }

    record Vacancy(String url) {}
    record LetterInput(String name, String resume, String vacancy, Letter.Language language,
                       Letter.Tone tone, String emphasis, boolean remoteConsent) {}

    @PostMapping(value = "/vacancy", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<VacancyImport.Imported> vacancy(@RequestBody Vacancy vacancy) {
        return privateResponse(vacancies.extract(text(vacancy.url(), 10, 2048, "link da vaga")));
    }

    @PostMapping(value = "/letters", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Letter.Result> letter(@RequestBody LetterInput input,
            @RequestHeader(value = "X-Orbe-Access", defaultValue = "") String code) {
        if (input.language() == null) {
            throw bad("Confirme o idioma da carta.");
        }
        if (writer.enabled() && writer.remote() && !input.remoteConsent()) {
            throw bad("Confirme o envio ao serviço de IA configurado nesta instância.");
        }
        String resume = text(input.resume(), 40, 12000, "currículo");
        boolean latex = resume.contains("\\begin{document}") || resume.contains("\\documentclass")
                || resume.contains("\\section{");
        var request = new Letter.Request(text(input.name(), 2, 100, "nome"),
                text(ResumeText.normalize(resume, latex), 40, 12000, "currículo"),
                text(input.vacancy(), 40, 10000, "descrição da vaga"), input.language(),
                input.tone() == null ? Letter.Tone.PROFESSIONAL : input.tone(),
                text(input.emphasis() == null ? "" : input.emphasis(), 0, 250, "destaque"));
        return privateResponse(writer.write(request, code));
    }

    @ExceptionHandler(ApiProblem.class)
    ResponseEntity<ProblemDetail> apiProblem(ApiProblem problem) {
        return ResponseEntity.status(problem.status).cacheControl(CacheControl.noStore())
                .body(ProblemDetail.forStatusAndDetail(problem.status, problem.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> uploadLimit() {
        return apiProblem(new ApiProblem(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter até 2 MB."));
    }

    private static <T> ResponseEntity<T> privateResponse(T content) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content);
    }

    private static String text(String value, int min, int max, String label) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() < min || clean.length() > max || clean.indexOf('\0') >= 0) {
            throw bad("O campo " + label + " deve ter entre " + min + " e " + max + " caracteres válidos.");
        }
        return clean;
    }

    private static ApiProblem bad(String message) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, message);
    }
}
