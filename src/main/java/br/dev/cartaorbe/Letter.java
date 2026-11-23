package br.dev.cartaorbe;

import java.util.List;
import java.util.regex.Pattern;

final class Letter {
    static final int MIN_WORDS = 250;
    static final int MAX_WORDS = 380;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:[’'-][\\p{L}\\p{N}]+)*");
    private static final Pattern DASH = Pattern.compile("\\s*(?:-{2,}|[–—―])\\s*");

    enum Language {
        PT("Brazilian Portuguese (pt-BR)", "Olá, equipe de recrutamento,", "Atenciosamente,"),
        EN("English (en)", "Dear Hiring Team,", "Sincerely,"),
        ES("Spanish (es)", "Estimado equipo de selección:", "Atentamente,");

        final String label;
        final String greeting;
        final String signOff;

        Language(String label, String greeting, String signOff) {
            this.label = label;
            this.greeting = greeting;
            this.signOff = signOff;
        }
    }

    enum Tone { PROFESSIONAL, FORMAL, DIRECT, ENTHUSIASTIC }

    record Request(String name, String resume, String vacancy, Language language, Tone tone, String emphasis) {}
    record Draft(List<String> paragraphs, List<String> evidence) {}
    record Result(String text, int wordCount, List<String> evidence, boolean example) {}

    static Result format(Request request, Draft draft, boolean example) {
        if (draft == null || draft.paragraphs() == null || draft.paragraphs().size() < 3
                || draft.paragraphs().size() > 4 || draft.evidence() == null
                || draft.evidence().isEmpty() || draft.evidence().size() > 5) {
            throw new IllegalArgumentException("São necessários três ou quatro parágrafos e até cinco trechos do CV.");
        }

        List<String> paragraphs = draft.paragraphs().stream().map(Letter::paragraph).toList();
        String text = String.join("\n\n", request.language().greeting, String.join("\n\n", paragraphs),
                request.language().signOff + "\n" + paragraph(request.name()));
        int words = (int) WORD.matcher(text).results().count();
        if (words < MIN_WORDS || words > MAX_WORDS) {
            throw new IllegalArgumentException("A carta ficou com " + words + " palavras. Ajuste para 250 a 380.");
        }

        String resume = compact(request.resume());
        List<String> evidence = draft.evidence().stream().map(Letter::compact).distinct().toList();
        if (evidence.stream().anyMatch(quote -> quote.length() < 15 || quote.length() > 500 || !resume.contains(quote))) {
            throw new IllegalArgumentException("Cada evidência deve ser uma citação literal do CV.");
        }
        return new Result(text, words, evidence, example);
    }

    private static String paragraph(String value) {
        String result = DASH.matcher(compact(value)).replaceAll(", ");
        if (result.isBlank()) {
            throw new IllegalArgumentException("Há um parágrafo vazio.");
        }
        return result;
    }

    private static String compact(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Há um campo ausente.");
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    private Letter() {}
}
