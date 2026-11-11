package de.lardtom.camdrones.planner.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record MissionWindowLayout(Rect window, Rect header, Rect title, Rect droneLine, Rect missionLine, Rect stateLine, Rect exitRow,
                                  int exitLabelWidth, Rect start, Rect pause, Rect stop, Rect footer, Rect hint, Rect close, Rect viewRow) {
    public static final int MARGIN = 8;
    public static final int MAX_WIDTH = 300;
    public static final int MIN_WIDTH = 200;
    public static final int HEADER = 24;
    public static final int FOOTER = 24;
    public static final int PAD = 6;
    public static final int GAP = 4;
    public static final int LINE = 12;
    public static final int ROW = 14;
    public static final int BUTTON = 16;
    public static final int BUTTON_PAD = 12;

    public record Widths(int exitLabel, int exitValue, int start, int pause, int stop, int close, int viewLabel, int viewValue) {
    }

    public static MissionWindowLayout compute(int width, int height, Widths texts) {
        int w = Math.max(Math.min(MIN_WIDTH, width), Math.min(width - 2 * MARGIN, MAX_WIDTH));
        int need = HEADER + PAD + 3 * LINE + 2 * 2 + PAD + ROW + 2 + ROW + PAD + BUTTON + PAD + FOOTER;
        int h = Math.min(Math.max(0, height - 2 * MARGIN), need);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        Rect window = new Rect(x, y, w, h);
        Rect header = new Rect(x, y, w, HEADER);
        Rect title = new Rect(x + PAD, y, w - 2 * PAD, HEADER);
        int inner = w - 2 * PAD;
        Rect droneLine = new Rect(x + PAD, y + HEADER + PAD, inner, LINE);
        Rect missionLine = droneLine.below(2, LINE);
        Rect stateLine = missionLine.below(2, LINE);
        int exitLabelWidth = Math.min(inner / 2, Math.max(texts.exitLabel(), texts.viewLabel()) + EditorLayout.LABEL_GAP + 2);
        Rect exitRow = new Rect(x + PAD, stateLine.bottom() + PAD, inner, ROW);
        Rect viewRow = exitRow.below(2, ROW);
        int buttonsY = viewRow.bottom() + PAD;
        Rect start = new Rect(x + PAD, buttonsY, texts.start() + BUTTON_PAD, BUTTON);
        Rect pause = new Rect(start.right() + GAP, buttonsY, texts.pause() + BUTTON_PAD, BUTTON);
        Rect stop = new Rect(pause.right() + GAP, buttonsY, texts.stop() + BUTTON_PAD, BUTTON);
        Rect footer = new Rect(x, y + h - FOOTER, w, FOOTER);
        int closeW = Math.min(inner / 2, texts.close() + BUTTON_PAD);
        Rect close = new Rect(x + w - PAD - closeW, footer.y() + (FOOTER - BUTTON) / 2, closeW, BUTTON);
        Rect hint = new Rect(x + PAD, footer.y(), Math.max(0, close.x() - GAP - x - PAD), FOOTER);
        return new MissionWindowLayout(window, header, title, droneLine, missionLine, stateLine, exitRow, exitLabelWidth, start, pause, stop,
                footer, hint, close, viewRow);
    }

    public Rect exitValue() {
        return EditorLayout.valueBox(this.exitRow, this.exitLabelWidth);
    }

    public Rect viewValue() {
        return EditorLayout.valueBox(this.viewRow, this.exitLabelWidth);
    }

    public Map<String, Rect> controls() {
        Map<String, Rect> out = new LinkedHashMap<>();
        out.put("title", this.title);
        out.put("droneLine", this.droneLine);
        out.put("missionLine", this.missionLine);
        out.put("stateLine", this.stateLine);
        out.put("exitRow", this.exitRow);
        out.put("viewRow", this.viewRow);
        out.put("start", this.start);
        out.put("pause", this.pause);
        out.put("stop", this.stop);
        out.put("hint", this.hint);
        out.put("close", this.close);
        return out;
    }

    public List<String> problems(int screenWidth, int screenHeight, Widths texts) {
        List<String> problems = new ArrayList<>();
        if (!this.window.inside(new Rect(0, 0, screenWidth, screenHeight))) {
            problems.add("Fenster ragt hinaus " + this.window);
        }
        List<Map.Entry<String, Rect>> list = new ArrayList<>(this.controls().entrySet());
        for (Map.Entry<String, Rect> e : list) {
            if (e.getValue().isEmpty()) {
                problems.add(e.getKey() + " leer");
            } else if (!e.getValue().inside(this.window)) {
                problems.add(e.getKey() + " außerhalb des Fensters " + e.getValue());
            }
        }
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(i).getValue().overlaps(list.get(j).getValue())) {
                    problems.add(list.get(i).getKey() + " überlappt " + list.get(j).getKey());
                }
            }
        }
        if (this.stop.bottom() > this.footer.y()) {
            problems.add("Knöpfe im Fuß");
        }
        if (texts.exitLabel() > this.exitLabelWidth - EditorLayout.LABEL_GAP) {
            problems.add("Beschriftung Verlassen zu breit");
        }
        if (texts.exitValue() > EditorLayout.valueTextWidth(this.exitValue())) {
            problems.add("Wert Verlassen zu breit");
        }
        if (texts.viewLabel() > this.exitLabelWidth - EditorLayout.LABEL_GAP) {
            problems.add("Beschriftung Kamera-Ansicht zu breit");
        }
        if (texts.viewValue() > EditorLayout.valueTextWidth(this.viewValue())) {
            problems.add("Wert Kamera-Ansicht zu breit");
        }
        if (texts.close() + 4 > this.close.w()) {
            problems.add("Knopf Zurück zu schmal");
        }
        return problems;
    }
}
