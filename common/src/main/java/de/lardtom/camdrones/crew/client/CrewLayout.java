package de.lardtom.camdrones.crew.client;

import de.lardtom.camdrones.planner.model.Rect;

import java.util.ArrayList;
import java.util.List;

public record CrewLayout(Rect window, Rect header, Rect title, Rect operatorLine, Rect viewButton, Rect releaseButton, Rect nearbyTitle,
                         List<Row> rows, Rect more, int hidden, Rect footer, Rect hint, Rect closeButton) {
    public static final int MARGIN = 8;
    public static final int MAX_WIDTH = 300;
    public static final int MIN_WIDTH = 200;
    public static final int HEADER = 24;
    public static final int FOOTER = 24;
    public static final int PAD = 6;
    public static final int GAP = 4;
    public static final int LINE = 12;
    public static final int BUTTON = 16;
    public static final int SECTION = 14;
    public static final int ROW = 18;
    public static final int BUTTON_PAD = 12;

    public record Row(Rect area, Rect name, Rect distance, Rect button) {
    }

    public CrewLayout {
        rows = List.copyOf(rows);
    }

    public static CrewLayout compute(int width, int height, int players, int inviteText, int viewText, int releaseText, int closeText,
                                     int distanceText) {
        int w = Math.max(Math.min(MIN_WIDTH, width), Math.min(width - 2 * MARGIN, MAX_WIDTH));
        int need = HEADER + PAD + LINE + 2 + BUTTON + PAD + SECTION + 2 + Math.max(1, players) * ROW + PAD + FOOTER;
        int h = Math.max(Math.min(height, HEADER + FOOTER + 4 * ROW), Math.min(height - 2 * MARGIN, need));
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        Rect window = new Rect(x, y, w, h);
        Rect header = new Rect(x, y, w, HEADER);
        Rect title = new Rect(x + PAD, y, w - 2 * PAD, HEADER);
        int inner = w - 2 * PAD;
        int top = y + HEADER + PAD;
        Rect operatorLine = new Rect(x + PAD, top, inner, LINE);
        int buttonsY = operatorLine.bottom() + 2;
        int half = (inner - GAP) / 2;
        int viewW = Math.min(half, viewText + BUTTON_PAD);
        int releaseW = Math.min(half, releaseText + BUTTON_PAD);
        Rect viewButton = new Rect(x + PAD, buttonsY, viewW, BUTTON);
        Rect releaseButton = new Rect(viewButton.right() + GAP, buttonsY, releaseW, BUTTON);
        Rect nearbyTitle = new Rect(x + PAD, buttonsY + BUTTON + PAD, inner, SECTION);
        Rect footer = new Rect(x, y + h - FOOTER, w, FOOTER);
        int closeW = Math.min(inner / 2, closeText + BUTTON_PAD);
        Rect closeButton = new Rect(x + w - PAD - closeW, footer.y() + (FOOTER - BUTTON) / 2, closeW, BUTTON);
        Rect hint = new Rect(x + PAD, footer.y(), Math.max(0, closeButton.x() - GAP - x - PAD), FOOTER);
        int rowsTop = nearbyTitle.bottom() + 2;
        int available = Math.max(0, footer.y() - PAD - rowsTop);
        int shown = Math.min(players, available / ROW);
        if (shown < players) {
            shown = Math.max(0, Math.min(players, (available - LINE) / ROW));
        }
        int inviteW = Math.min(inner / 2, inviteText + BUTTON_PAD);
        int distanceW = Math.min(inner / 4, distanceText + 4);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < shown; i++) {
            Rect area = new Rect(x + PAD, rowsTop + i * ROW, inner, ROW - 2);
            Rect button = new Rect(area.right() - inviteW, area.y(), inviteW, ROW - 2);
            Rect distance = new Rect(button.x() - GAP - distanceW, area.y(), distanceW, ROW - 2);
            Rect name = new Rect(area.x() + 2, area.y(), Math.max(0, distance.x() - GAP - area.x() - 2), ROW - 2);
            rows.add(new Row(area, name, distance, button));
        }
        int hidden = players - shown;
        Rect more = hidden > 0 ? new Rect(x + PAD, rowsTop + shown * ROW, inner, LINE) : Rect.EMPTY;
        return new CrewLayout(window, header, title, operatorLine, viewButton, releaseButton, nearbyTitle, rows, more, hidden, footer, hint,
                closeButton);
    }
}
