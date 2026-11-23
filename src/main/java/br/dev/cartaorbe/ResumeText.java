package br.dev.cartaorbe;

import java.util.regex.Pattern;

final class ResumeText {
    private static final Pattern COMMENTS = Pattern.compile("(?m)(?<!\\\\)%.*$");
    private static final Pattern FORMATTING = Pattern.compile(
            "\\\\(?:begin|end|documentclass|usepackage|label|vspace|hspace|setlength|includegraphics)"
            + "\\*?(?:\\[[^\\]\\r\\n]*])?(?:\\{[^{}\\r\\n]*})+");
    private static final Pattern COMMAND = Pattern.compile("\\\\[a-zA-Z@]+\\*?(?:\\[[^\\]\\r\\n]*])?");
    private static final Pattern ESCAPE = Pattern.compile("\\\\([%&#_$])");
    private static final Pattern MARK = Pattern.compile("\\\\[’'`^\"~]\\{?([a-zA-Z])}?");

    static String normalize(String input, boolean latex) {
        String text = input.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
        if (latex) {
            int start = text.indexOf("\\begin{document}");
            if (start >= 0) {
                text = text.substring(start + "\\begin{document}".length());
                int end = text.indexOf("\\end{document}");
                if (end >= 0) {
                    text = text.substring(0, end);
                }
            }
            text = COMMENTS.matcher(text).replaceAll("");
            text = FORMATTING.matcher(text).replaceAll("");
            text = text.replaceAll("\\\\item\\b", "\n• ").replace("\\\\", "\n");
            text = MARK.matcher(text).replaceAll("$1");
            text = COMMAND.matcher(text).replaceAll("");
            text = ESCAPE.matcher(text).replaceAll("$1");
            text = text.replace('{', ' ').replace('}', ' ').replace('~', ' ').replaceAll("[ \\t]+", " ");
        }
        return text.strip();
    }

    private ResumeText() {}
}
