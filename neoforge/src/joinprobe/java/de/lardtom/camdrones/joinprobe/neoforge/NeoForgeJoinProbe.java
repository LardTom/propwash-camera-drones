package de.lardtom.camdrones.joinprobe.neoforge;

import de.lardtom.camdrones.joinprobe.BareJoinProbe;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "camdrones_joinprobe", dist = Dist.CLIENT)
public final class NeoForgeJoinProbe {
    public NeoForgeJoinProbe() {
        if (BareJoinProbe.enabled()) {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> BareJoinProbe.tick(Minecraft.getInstance()));
        }
    }
}
