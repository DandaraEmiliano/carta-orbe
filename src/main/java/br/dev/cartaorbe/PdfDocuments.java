package br.dev.cartaorbe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class PdfDocuments {
    private static final int MAX_PAGES = 12;
    private static final int MAX_RESUME_CHARS = 12000;
    private static final float MARGIN = 56.7f;
    private static final float FONT_SIZE = 11.5f;
    private static final float LINE_HEIGHT = 16f;

    String extract(byte[] input) {
        try (PDDocument document = Loader.loadPDF(input)) {
            if (document.isEncrypted()) {
                throw invalid("Envie uma cópia do PDF sem proteção por senha.");
            }
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw invalid("O PDF do currículo deve ter até 12 páginas.");
            }

            var extractor = new PDFTextStripper();
            extractor.setSortByPosition(true);
            var text = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                extractor.setStartPage(page);
                extractor.setEndPage(page);
                String pageText = extractor.getText(document).strip();
                if (text.length() + pageText.length() > MAX_RESUME_CHARS) {
                    throw invalid("O PDF excede 12.000 caracteres. Exporte ou cole as seções mais relevantes do currículo.");
                }
                if (!pageText.isEmpty()) {
                    text.append(pageText).append('\n');
                }
            }
            if (text.toString().strip().length() < 40) {
                throw invalid("O PDF tem pouco texto selecionável. Se for uma imagem digitalizada, use OCR ou cole o currículo em texto.");
            }
            return text.toString().strip();
        } catch (InvalidPasswordException protectedPdf) {
            throw invalid("Envie uma cópia do PDF sem proteção por senha.");
        } catch (IOException | IllegalArgumentException malformed) {
            throw invalid("Não consegui ler o PDF. Exporte uma nova cópia ou cole o currículo em texto.");
        }
    }

    byte[] letter(String input) throws IOException {
        var font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        String text = input.replace("\r\n", "\n").replace('\r', '\n').replaceAll("\\h+", " ");
        List<String> lines;
        try {
            lines = wrap(text, font, PDRectangle.A4.getWidth() - 2 * MARGIN);
        } catch (IllegalArgumentException invalidGlyph) {
            throw invalid("Remova símbolos incomuns do texto ou use a opção Imprimir / PDF do navegador.");
        }

        if (lines.size() * LINE_HEIGHT + 2 * MARGIN > PDRectangle.A4.getHeight()) {
            throw invalid("O texto editado não cabe em uma página A4. Encurte a carta e tente novamente.");
        }

        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Carta de apresentação | Carta Orbe");
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, FONT_SIZE);
                stream.setLeading(LINE_HEIGHT);
                stream.newLineAtOffset(MARGIN, PDRectangle.A4.getHeight() - MARGIN - FONT_SIZE);
                for (String line : lines) {
                    stream.showText(line);
                    stream.newLine();
                }
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static List<String> wrap(String text, PDFont font, float width) throws IOException {
        var lines = new ArrayList<String>();
        for (String paragraph : text.split("\n", -1)) {
            var line = new StringBuilder();
            float usedWidth = 0;
            float space = font.getStringWidth(" ") * FONT_SIZE / 1000;
            for (String word : paragraph.strip().split(" +")) {
                float wordWidth = font.getStringWidth(word) * FONT_SIZE / 1000;
                if (wordWidth > width) {
                    throw invalid("Há uma palavra ou link longo demais para a página. Encurte esse trecho e tente novamente.");
                }
                if (!line.isEmpty() && usedWidth + space + wordWidth > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                    usedWidth = 0;
                }
                if (!line.isEmpty()) {
                    line.append(' ');
                    usedWidth += space;
                }
                line.append(word);
                usedWidth += wordWidth;
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static ApiProblem invalid(String message) {
        return new ApiProblem(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
