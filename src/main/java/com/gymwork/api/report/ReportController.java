package com.gymwork.api.report;

import com.gymwork.api.common.Slug;
import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.report.ReportLoader.Loaded;
import com.gymwork.api.report.dto.AiPackage;
import com.gymwork.api.report.dto.AnalysisReport;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.user.UserTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relatórios de sessão, semana e fase. O texto responde {@code text/plain} (copiar) ou, com
 * {@code download=true}, um arquivo .txt; {@code format=csv|pdf} sempre baixa o arquivo.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Relatórios")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class ReportController {

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType MARKDOWN = new MediaType("text", "markdown", StandardCharsets.UTF_8);

    private final ReportLoader loader;
    private final ReportAnalysisService analysisService;
    private final AiPackageService aiPackageService;
    private final UserTime userTime;

    public ReportController(
            ReportLoader loader,
            ReportAnalysisService analysisService,
            AiPackageService aiPackageService,
            UserTime userTime) {
        this.loader = loader;
        this.analysisService = analysisService;
        this.aiPackageService = aiPackageService;
        this.userTime = userTime;
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Relatório da sessão (format=text|csv|pdf, download=true para baixar o texto)")
    public ResponseEntity<byte[]> session(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "text") String format,
            @RequestParam(defaultValue = "false") boolean download) {
        return render(userId, loader.session(userId, id), format, download);
    }

    @GetMapping("/weeks/{id}")
    @Operation(summary = "Relatório da semana do plano (format=text|csv|pdf)")
    public ResponseEntity<byte[]> week(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "text") String format,
            @RequestParam(defaultValue = "false") boolean download) {
        return render(userId, loader.week(userId, id), format, download);
    }

    @GetMapping("/phases/{id}")
    @Operation(summary = "Relatório da fase completa (format=text|csv|pdf)")
    public ResponseEntity<byte[]> phase(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "text") String format,
            @RequestParam(defaultValue = "false") boolean download) {
        return render(userId, loader.phase(userId, id), format, download);
    }

    @GetMapping("/sessions/{id}/analysis")
    @Operation(summary = "Resumo de análise da sessão")
    public AnalysisReport sessionAnalysis(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return analysisService.session(userId, id);
    }

    @GetMapping("/weeks/{id}/analysis")
    @Operation(summary = "Resumo de análise da semana")
    public AnalysisReport weekAnalysis(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return analysisService.week(userId, id);
    }

    @GetMapping("/phases/{id}/analysis")
    @Operation(summary = "Resumo de análise da fase")
    public AnalysisReport phaseAnalysis(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return analysisService.phase(userId, id);
    }

    @GetMapping("/phases/{id}/ai-package")
    @Operation(summary = "Pacote para IA da fase (format=md|json; download=true baixa como arquivo)")
    public ResponseEntity<byte[]> aiPackage(
            @CurrentUserId UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "md") String format,
            @RequestParam(defaultValue = "false") boolean download) {
        AiPackage aiPackage = aiPackageService.build(userId, id);
        String name = "pacote-ia-" + Slug.of(aiPackage.context().phaseName());
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "md" -> body(aiPackageService.markdown(aiPackage).getBytes(StandardCharsets.UTF_8), MARKDOWN,
                    download ? name + ".md" : null);
            case "json" -> body(aiPackageService.json(aiPackage).getBytes(StandardCharsets.UTF_8),
                    MediaType.APPLICATION_JSON, download ? name + ".json" : null);
            default -> throw new InvalidFieldException("format", "Formato inválido: use md ou json");
        };
    }

    private ResponseEntity<byte[]> render(UUID userId, Loaded loaded, String format, boolean download) {
        Report report = loaded.report();
        String name = fileName(report);
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "text", "txt" -> body(
                    ReportText.text(report, userTime.weightUnit(userId)).getBytes(StandardCharsets.UTF_8),
                    TEXT,
                    download ? name + ".txt" : null);
            case "csv" -> body(ReportCsv.csv(report).getBytes(StandardCharsets.UTF_8), CSV, name + ".csv");
            case "pdf" -> body(
                    ReportPdf.pdf(ReportText.lines(report, userTime.weightUnit(userId)),
                            "Gerado pelo Gym Work em " + ReportLabels.date(userTime.today(userId))),
                    MediaType.APPLICATION_PDF,
                    name + ".pdf");
            default -> throw new InvalidFieldException("format", "Formato inválido: use text, csv ou pdf");
        };
    }

    /** "relatorio-fase-4-controle-semana-1", "relatorio-sessao-2026-11-26"... */
    static String fileName(Report report) {
        String base = report.phaseName() == null ? "treino-avulso" : Slug.of(report.phaseName());
        return switch (report.scope()) {
            case SESSION -> "relatorio-" + base + "-" + report.from();
            case WEEK -> "relatorio-" + base + (report.weeks().isEmpty() || report.weeks().get(0).number() == null
                    ? ""
                    : "-semana-" + report.weeks().get(0).number());
            case PHASE -> "relatorio-" + base;
        };
    }

    private static ResponseEntity<byte[]> body(byte[] content, MediaType type, String attachment) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(type);
        if (attachment != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(attachment).build().toString());
        }
        return response.body(content);
    }
}
