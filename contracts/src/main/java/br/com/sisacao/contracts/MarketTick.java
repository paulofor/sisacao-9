package br.com.sisacao.contracts;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketTick(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.#-]{1,32}") String symbol,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 8) BigDecimal bid,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 8) BigDecimal ask,
        @NotNull Instant observedAt,
        @NotNull Source source) {
    public enum Source { SIMULATED, MT5 }
}
