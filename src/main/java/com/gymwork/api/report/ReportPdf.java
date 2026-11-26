package com.gymwork.api.report;

import com.gymwork.api.report.ReportText.Line;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * PDF a partir das mesmas linhas do texto: títulos em negrito no lugar das molduras e linhas no
 * lugar dos separadores. Usa as fontes padrão do PDF (Helvetica, CP1252), que cobrem os acentos;
 * a seta da substituição vira "->".
 */
public final class ReportPdf {

    private static final Font TITLE = font(FontFactory.HELVETICA_BOLD, 16);
    private static final Font WEEK = font(FontFactory.HELVETICA_BOLD, 13);
    private static final Font DAY = font(FontFactory.HELVETICA_BOLD, 11);
    private static final Font TEXT = font(FontFactory.HELVETICA, 10);
    private static final Font FOOTER = font(FontFactory.HELVETICA_OBLIQUE, 8);

    private ReportPdf() {
    }

    public static byte[] pdf(List<Line> lines, String footer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        PdfWriter.getInstance(document, out);
        document.addTitle("Relatório Gym Work");
        document.addCreator("Gym Work");
        document.open();
        for (Line line : lines) {
            switch (line.kind()) {
                case RULE, BLANK -> {
                    // molduras e linhas em branco viram espaçamento dos parágrafos
                }
                case TITLE -> {
                    Paragraph title = paragraph(line.text(), TITLE, 0, 6);
                    title.setAlignment(Element.ALIGN_LEFT);
                    document.add(title);
                    document.add(new Chunk(new LineSeparator(1.2f, 100, null, Element.ALIGN_CENTER, -2)));
                }
                case WEEK -> document.add(paragraph(line.text(), WEEK, 14, 2));
                case DAY -> document.add(paragraph(line.text(), DAY, 8, 4));
                case TEXT -> document.add(paragraph(line.text(), TEXT, "Observações:".equals(line.text()) ? 6 : 0, 1));
                case SEPARATOR -> {
                    Paragraph separator = new Paragraph();
                    separator.add(new Chunk(new LineSeparator(0.5f, 100, null, Element.ALIGN_CENTER, -2)));
                    separator.setSpacingAfter(4);
                    document.add(separator);
                }
            }
        }
        if (footer != null) {
            document.add(paragraph(footer, FOOTER, 12, 0));
        }
        document.close();
        return out.toByteArray();
    }

    private static Paragraph paragraph(String text, Font font, float before, float after) {
        Paragraph paragraph = new Paragraph(pdfText(text), font);
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(after);
        return paragraph;
    }

    /** Troca os caracteres que não existem nas fontes padrão (CP1252). */
    static String pdfText(String text) {
        return text.replace("→", "->").replace("═", "=");
    }

    private static Font font(String name, float size) {
        return FontFactory.getFont(name, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, size);
    }
}
