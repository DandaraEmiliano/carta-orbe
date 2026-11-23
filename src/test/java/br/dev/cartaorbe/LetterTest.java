package br.dev.cartaorbe;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LetterTest {
    private static final String SOURCE = "Implementei rastreamento de entregas.";
    private static final Letter.Request INPUT = new Letter.Request("Marina Costa", SOURCE, "Vaga em logística",
            Letter.Language.PT, Letter.Tone.PROFESSIONAL, "");

    @Test
    void normalizesDashesAndAddsLocalizedGreetingAndSignature() {
        String paragraph = ("Resultado concreto de logística — para o time. ").repeat(13);
        var result = Letter.format(INPUT, new Letter.Draft(List.of(paragraph, paragraph, paragraph), List.of(SOURCE)), false);
        assertThat(result.text()).startsWith(Letter.Language.PT.greeting).endsWith("Atenciosamente,\nMarina Costa")
                .doesNotContain("—", "--");
    }

    @Test
    void rejectsAQuoteMissingFromTheResume() {
        String paragraph = "Experiência com projetos para a equipe de rastreamento. ".repeat(11);
        var draft = new Letter.Draft(List.of(paragraph, paragraph, paragraph), List.of("Gerenciei uma equipe de dez pessoas."));
        assertThatThrownBy(() -> Letter.format(INPUT, draft, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("citação literal");
    }
}
