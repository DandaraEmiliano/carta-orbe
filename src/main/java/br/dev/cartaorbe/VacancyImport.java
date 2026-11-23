package br.dev.cartaorbe;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class VacancyImport {
    private static final Set<String> ATS = Set.of("job-boards.greenhouse.io", "boards.greenhouse.io",
            "jobs.lever.co", "jobs.ashbyhq.com");

    record Imported(String description, boolean truncated) {}

    Imported extract(String input) {
        URI uri;
        try {
            uri = URI.create(input);
        } catch (IllegalArgumentException invalid) {
            throw invalidLink();
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || uri.getPort() != -1 || !ATS.contains(host.toLowerCase(Locale.ROOT))
                || uri.getRawPath() == null || uri.getRawPath().length() < 2) {
            throw invalidLink();
        }

        try {
            // The exact host allowlist and disabled redirects prevent requests to arbitrary destinations.
            var page = Jsoup.connect(uri.toASCIIString()).timeout(5000).maxBodySize(1_048_576)
                    .followRedirects(false).execute();
            if (page.statusCode() != 200) {
                throw unavailable();
            }
            var document = page.parse();
            Element content = document.selectFirst(".posting-page, main, article, #content");
            if (content == null) {
                throw unavailable();
            }
            content.select("script, style, nav, footer, form").remove();
            String description = content.wholeText().strip().replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
            if (description.length() < 150) {
                throw unavailable();
            }
            boolean truncated = description.length() > 10000;
            return new Imported(truncated ? description.substring(0, 10000).stripTrailing() : description, truncated);
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static ApiProblem invalidLink() {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY,
                "A importação aceita links HTTPS do Greenhouse, Lever ou Ashby. Se preferir, cole a descrição da vaga.");
    }

    private static ApiProblem unavailable() {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Não consegui extrair a vaga. Cole a descrição ou os requisitos principais.");
    }
}
