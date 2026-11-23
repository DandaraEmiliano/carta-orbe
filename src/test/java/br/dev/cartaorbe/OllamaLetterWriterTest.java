package br.dev.cartaorbe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaLetterWriterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void retriesADraftInTheWrongLanguageAndResolvesEvidenceIds() throws Exception {
        String source = "Desenvolvi APIs para rastreamento de entregas com Java.";
        String paragraph = "I build integrations with measurable improvements for teams. ".repeat(11).strip();
        String portuguese = "Trabalhei com minha equipe para melhorar o rastreamento das entregas. ".repeat(9).strip();
        byte[] mixedDraft = response(List.of(paragraph, portuguese, paragraph), List.of(2));
        byte[] validDraft = response(List.of(paragraph, paragraph, paragraph), List.of(2, 99));

        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", call -> {
            try (call) {
                call.getRequestBody().readAllBytes();
                byte[] response = calls.getAndIncrement() == 0 ? mixedDraft : validDraft;
                call.getResponseHeaders().set("Content-Type", "application/json");
                call.sendResponseHeaders(200, response.length);
                call.getResponseBody().write(response);
            }
        });
        server.start();
        try {
            var writer = new OllamaLetterWriter(mapper, "http://127.0.0.1:" + server.getAddress().getPort(),
                    "test-model", true, "", "");
            var request = new Letter.Request("Marina Costa", "Experiência\n" + source,
                    "A role developing shipment tracking APIs.", Letter.Language.EN, Letter.Tone.FORMAL, "");
            var result = writer.write(request, "");

            assertThat(calls.get()).isEqualTo(2);
            assertThat(result.example()).isFalse();
            assertThat(result.text()).startsWith("Dear Hiring Team,").endsWith("Sincerely,\nMarina Costa");
            assertThat(result.evidence()).containsExactly(source);
        } finally {
            server.stop(0);
        }
    }

    private byte[] response(List<String> paragraphs, List<Integer> evidenceIds) throws Exception {
        String draft = mapper.writeValueAsString(Map.of("paragraphs", paragraphs, "evidenceIds", evidenceIds));
        return mapper.writeValueAsBytes(Map.of("message", Map.of("content", draft)));
    }
}
