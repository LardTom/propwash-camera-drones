package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.TabSection;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

public final class CamTab {
    public static final Identifier ICON = GimbalTable.TOUR;
    public static final List<TabSection> ORDER = List.of(TabSection.PRESETS, TabSection.FRAMES, TabSection.STACKS, TabSection.MOTORS,
            TabSection.PROPS, TabSection.BATTERIES, TabSection.VIDEO, TabSection.ACCESSORIES, TabSection.ENTRIES);

    private static ExtensionTab tab;

    private CamTab() {
    }

    public static synchronized ExtensionTab init() {
        if (tab == null) {
            tab = PropwashApi.extensionTab(CamDrones.MOD_ID).icon(ICON).order(ORDER.toArray(TabSection[]::new));
            tab.add(LensItems::stacks);
        }
        return tab;
    }

    public static synchronized Optional<ExtensionTab> tab() {
        return Optional.ofNullable(tab);
    }
}
