package br.com.sisacao.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record AnalysisRequest(@NotNull UUID runId, @NotEmpty @Size(max = 50) List<@NotNull @Valid MarketTick> ticks) {}
