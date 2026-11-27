package com.gymwork.api.dashboard;

import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Tela inicial: plano, fase e semana atuais, próximo treino, últimos treinos, peso, "
            + "frequência, exercícios em progresso/estáveis/em queda, séries por grupo e observações recentes")
    public DashboardResponse dashboard(@CurrentUserId UUID userId) {
        return dashboardService.dashboard(userId);
    }
}
