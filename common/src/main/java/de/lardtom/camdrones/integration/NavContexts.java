package de.lardtom.camdrones.integration;

import java.util.Objects;
import java.util.function.Supplier;

public final class NavContexts {
    private static volatile Supplier<? extends NavContext> pilot = () -> NavContext.NONE;

    private NavContexts() {
    }

    public static void pilot(Supplier<? extends NavContext> supplier) {
        pilot = Objects.requireNonNull(supplier, "supplier");
    }

    public static NavContext pilot() {
        NavContext context = pilot.get();
        return context == null ? NavContext.NONE : context;
    }
}
