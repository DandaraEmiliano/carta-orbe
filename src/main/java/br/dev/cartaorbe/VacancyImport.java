package br.dev.cartaorbe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class VacancyImport {
    private static final String SLUG = "([a-zA-Z0-9][a-zA-Z0-9_-]{0,99})";
    private static final String UUID = "([a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12})";
    private static final Pattern GREENHOUSE = Pattern.compile("/" + SLUG + "/jobs/([0-9]{1,20})/?");
    private static final Pattern JOB = Pattern.compile("/" + SLUG + "/" + UUID + "(?:/apply)?/?");
    private static final int MAX_DESCRIPTION = 10000;
    private final ObjectMapper mapper;

    record Imported(String description, boolean truncated) {}

    VacancyImport(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    Imported extract(String input) {
        URI uri;
        try {
            uri = URI.create(input);
        } catch (IllegalArgumentException invalid) {
            throw invalidLink();
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getRawPath() == null) {
            throw invalidLink();
        }

        String path = uri.getRawPath();
        try {
            String description = switch (host.toLowerCase(Locale.ROOT)) {
                case "job-boards.greenhouse.io", "boards.greenhouse.io" -> greenhouse(path);
                case "jobs.lever.co" -> lever(path, "api.lever.co");
                case "jobs.eu.lever.co" -> lever(path, "api.eu.lever.co");
                case "jobs.ashbyhq.com" -> ashby(path);
                default -> throw invalidLink();
            };

            if (description.length() < 100) {
                throw unavailable();
            }
            boolean truncated = description.length() > MAX_DESCRIPTION;
            return new Imported(truncated ? description.substring(0, MAX_DESCRIPTION).stripTrailing() : description,
                    truncated);
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private String greenhouse(String path) throws IOException {
        var job = GREENHOUSE.matcher(path);
        if (!job.matches()) {
            throw invalidLink();
        }
        JsonNode data = read("https://boards-api.greenhouse.io/v1/boards/" + job.group(1) + "/jobs/" + job.group(2));
        return join(data.path("title").asText(), data.path("company_name").asText(),
                data.path("location").path("name").asText(),
                html(Parser.unescapeEntities(data.path("content").asText(), false)));
    }

    private String lever(String path, String apiHost) throws IOException {
        var job = JOB.matcher(path);
        if (!job.matches()) {
            throw invalidLink();
        }
        JsonNode data = read("https://" + apiHost + "/v0/postings/" + job.group(1) + "/" + job.group(2));
        JsonNode categories = data.path("categories");
        var requirements = new StringBuilder();
        for (JsonNode list : data.path("lists")) {
            requirements.append(join(list.path("text").asText(), html(list.path("content").asText()))).append("\n\n");
        }
        return join(data.path("text").asText(), job.group(1), categories.path("location").asText(),
                data.path("descriptionPlain").asText(), requirements.toString(), data.path("additionalPlain").asText());
    }

    private String ashby(String path) throws IOException {
        var job = JOB.matcher(path);
        if (!job.matches()) {
            throw invalidLink();
        }
        JsonNode board = read("https://api.ashbyhq.com/posting-api/job-board/" + job.group(1));
        String jobPath = "/" + job.group(1) + "/" + job.group(2);
        for (JsonNode posting : board.path("jobs")) {
            if (posting.path("jobUrl").asText().equalsIgnoreCase("https://jobs.ashbyhq.com" + jobPath)) {
                return join(posting.path("title").asText(), job.group(1), posting.path("location").asText(),
                        posting.path("descriptionPlain").asText());
            }
        }
        throw unavailable();
    }

    private JsonNode read(String endpoint) throws IOException {
        var response = Jsoup.connect(endpoint).timeout(5000).maxBodySize(2_097_152)
                .header("Accept", "application/json").ignoreContentType(true).followRedirects(false).execute();
        if (response.statusCode() != 200) {
            throw unavailable();
        }
        return mapper.readTree(response.body());
    }

    private static String html(String input) {
        var body = Jsoup.parseBodyFragment(input).body();
        body.select("script, style").remove();
        body.select("p,div,li,br,h1,h2,h3,h4").after("\n");
        return body.wholeText().replaceAll("\\n{3,}", "\n\n");
    }

    private static String join(String... parts) {
        return Arrays.stream(parts).filter(Objects::nonNull).map(String::strip)
                .filter(value -> !value.isBlank()).collect(Collectors.joining("\n\n"));
    }

    private static ApiProblem invalidLink() {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Use o link direto de uma vaga no Greenhouse, Lever ou Ashby, ou cole a descrição abaixo.");
    }

    private static ApiProblem unavailable() {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Não consegui extrair a vaga. Cole a descrição ou os requisitos principais.");
    }
}
