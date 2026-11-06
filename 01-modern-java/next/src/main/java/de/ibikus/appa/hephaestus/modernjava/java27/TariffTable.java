package de.ibikus.appa.hephaestus.modernjava.java27;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lazy constants (JEP 531, third preview in Java 27; formerly "stable values"): initialized at most once, on first
 * access, thread-safely, and treated as a constant by the JIT afterwards. Replaces double-checked locking and
 * holder-class idioms.
 */
public final class TariffTable {

    private final LazyConstant<Map<String, BigDecimal>> tariffs;

    public TariffTable(Supplier<Map<String, BigDecimal>> loader) {
        this.tariffs = LazyConstant.of(loader);
    }

    public Map<String, BigDecimal> get() {
        return tariffs.get();
    }
}
