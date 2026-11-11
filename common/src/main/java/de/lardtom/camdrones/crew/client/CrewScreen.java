package de.lardtom.camdrones.crew.client;

import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeDraw;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CrewScreen extends Screen implements ThemedScreen {
    public static final int REFRESH_TICKS = 20;

    public record Candidate(UUID id, String name, double distance) {
    }

    private final Entity drone;
    private final @Nullable Screen parent;
    private final Set<UUID> invited = new HashSet<>();
    private List<Candidate> candidates = List.of();
    private CrewLayout layout;
    private boolean shownOperated;
    private int age;

    public CrewScreen(Entity drone, @Nullable Screen parent) {
        super(Component.translatable(CrewTexts.SCREEN_TITLE));
        this.drone = drone;
        this.parent = parent;
    }

    public Entity drone() {
        return this.drone;
    }

    public CrewLayout layout() {
        return this.layout;
    }

    public List<Candidate> candidates() {
        return this.candidates;
    }

    @Override
    protected void init() {
        this.candidates = this.nearby();
        this.layout = CrewLayout.compute(this.width, this.height, this.candidates.size(),
                this.font.width(Component.translatable(CrewTexts.SCREEN_INVITED)) + 2,
                this.font.width(Component.translatable(CrewTexts.SCREEN_VIEW)),
                this.font.width(Component.translatable(CrewTexts.SCREEN_RELEASE)),
                this.font.width(Component.translatable(CrewTexts.SCREEN_CLOSE)),
                this.font.width(Component.translatable(CrewTexts.SCREEN_DISTANCE, "99,9")));
        CrewLayout l = this.layout;
        this.button(l.viewButton(), Component.translatable(CrewTexts.SCREEN_VIEW), FlatButton.Style.NORMAL, this::view);
        FlatButton release = this.button(l.releaseButton(), Component.translatable(CrewTexts.SCREEN_RELEASE), FlatButton.Style.DANGER,
                this::release);
        this.shownOperated = this.data().operated();
        release.active = this.shownOperated;
        for (int i = 0; i < l.rows().size(); i++) {
            Candidate candidate = this.candidates.get(i);
            boolean sent = this.invited.contains(candidate.id());
            FlatButton invite = this.button(l.rows().get(i).button(),
                    Component.translatable(sent ? CrewTexts.SCREEN_INVITED : CrewTexts.SCREEN_INVITE),
                    FlatButton.Style.PRIMARY, () -> this.invite(candidate));
            invite.active = !sent && !this.data().operatedBy(candidate.id());
        }
        this.button(l.closeButton(), Component.translatable(CrewTexts.SCREEN_CLOSE), FlatButton.Style.NORMAL, this::onClose);
    }

    private FlatButton button(Rect r, Component label, FlatButton.Style style, Runnable action) {
        return this.addRenderableWidget(new FlatButton(r.x(), r.y(), r.w(), r.h(), label, style, action));
    }

    private GimbalData data() {
        return CrewData.get(this.drone);
    }

    private List<Candidate> nearby() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) {
            return List.of();
        }
        UUID me = this.minecraft.player.getUUID();
        List<Candidate> out = new ArrayList<>();
        for (AbstractClientPlayer player : this.minecraft.level.players()) {
            if (player.getUUID().equals(me) || player.isSpectator()) {
                continue;
            }
            double distance = player.distanceTo(this.minecraft.player);
            if (distance <= CrewBook.MAX_DISTANCE) {
                out.add(new Candidate(player.getUUID(), player.getPlainTextName(), distance));
            }
        }
        out.sort(Comparator.comparingDouble(Candidate::distance).thenComparing(Candidate::name));
        return List.copyOf(out);
    }

    @Override
    public void tick() {
        if (this.drone.isRemoved()) {
            this.onClose();
            return;
        }
        if (++this.age % REFRESH_TICKS == 0) {
            List<Candidate> now = this.nearby();
            if (!ids(now).equals(ids(this.candidates)) || this.data().operated() != this.shownOperated) {
                this.rebuildWidgets();
            } else {
                this.candidates = now;
            }
        }
    }

    private static List<UUID> ids(List<Candidate> list) {
        return list.stream().map(Candidate::id).toList();
    }

    private void invite(Candidate candidate) {
        if (CamNetwork.sendToServer(CrewPackets.Request.invite(this.drone.getId(), candidate.id()))) {
            this.invited.add(candidate.id());
            this.rebuildWidgets();
        }
    }

    private void release() {
        CamNetwork.sendToServer(CrewPackets.Request.of(CrewPackets.Action.RELEASE, this.drone.getId()));
    }

    private void view() {
        if (CrewClient.cameraView(this.drone) && this.minecraft != null) {
            this.minecraft.gui.setScreen(null);
        }
    }

    private Component operatorText() {
        Optional<UUID> operator = this.data().operator();
        if (operator.isEmpty()) {
            return Component.translatable(CrewTexts.SCREEN_NO_OPERATOR);
        }
        String name = operator.get().toString();
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            PlayerInfo info = this.minecraft.getConnection().getPlayerInfo(operator.get());
            if (info != null) {
                name = info.getProfile().name();
            }
        }
        return Component.translatable(CrewTexts.SCREEN_OPERATOR, name);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ThemeDraw.backdrop(g, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        CrewLayout l = this.layout;
        Rect w = l.window();
        ThemeDraw.window(g, w.x(), w.y(), w.right(), w.bottom());
        ThemeDraw.headerBar(g, w.x(), w.y(), w.right(), l.header().h());
        ThemeText.fitted(g, this.font, this.title, l.title().x(), Metrics.textY(l.title().y(), l.title().h()), l.title().w(), Palette.TEXT);
        ThemeText.fitted(g, this.font, this.operatorText(), l.operatorLine().x(), Metrics.textY(l.operatorLine().y(), l.operatorLine().h()),
                l.operatorLine().w(), this.data().operated() ? Palette.TEXT : Palette.TEXT_MUTED);
        ThemeDraw.sectionTitle(g, this.font, Component.translatable(CrewTexts.SCREEN_NEARBY), l.nearbyTitle().x(), l.nearbyTitle().y(),
                l.nearbyTitle().w());
        if (this.candidates.isEmpty()) {
            Rect r = l.nearbyTitle().below(2, CrewLayout.LINE);
            ThemeText.fitted(g, this.font, Component.translatable(CrewTexts.SCREEN_NOBODY), r.x(), Metrics.textY(r.y(), r.h()), r.w(),
                    Palette.TEXT_FAINT);
        }
        for (int i = 0; i < l.rows().size() && i < this.candidates.size(); i++) {
            CrewLayout.Row row = l.rows().get(i);
            Candidate candidate = this.candidates.get(i);
            ThemeDraw.panel(g, row.area().x(), row.area().y(), row.area().right(), row.area().bottom());
            int textY = Metrics.textY(row.area().y(), row.area().h());
            ThemeText.fitted(g, this.font, candidate.name(), row.name().x(), textY, row.name().w(), Palette.TEXT);
            String distance = String.format(Locale.ROOT, "%.1f", candidate.distance());
            ThemeText.fittedRight(g, this.font, Component.translatable(CrewTexts.SCREEN_DISTANCE, distance.replace('.', decimal())),
                    row.distance().right(), textY, row.distance().w(), Palette.TEXT_MUTED);
        }
        if (l.hidden() > 0) {
            ThemeText.fitted(g, this.font, Component.translatable(CrewTexts.SCREEN_MORE, l.hidden()), l.more().x(),
                    Metrics.textY(l.more().y(), l.more().h()), l.more().w(), Palette.TEXT_FAINT);
        }
        ThemeDraw.footerBar(g, w.x(), l.footer().y(), w.right(), w.bottom());
        ThemeText.fitted(g, this.font, Component.translatable(CrewTexts.SCREEN_HINT), l.hint().x(), Metrics.textY(l.hint().y(), l.hint().h()),
                l.hint().w(), Palette.TEXT_FAINT);
        super.extractRenderState(g, mouseX, mouseY, a);
        ThemeDraw.windowBorder(g, w.x(), w.y(), w.right(), w.bottom());
    }

    private static char decimal() {
        String separator = I18n.get(CrewTexts.SCREEN_DECIMAL);
        return separator.length() == 1 ? separator.charAt(0) : '.';
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
