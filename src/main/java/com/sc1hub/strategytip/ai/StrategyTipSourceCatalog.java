package com.sc1hub.strategytip.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrategyTipSourceCatalog {

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("t_vs_z", "테저전", "tvszboard"),
            new Entry("t_vs_p", "테프전", "tvspboard"),
            new Entry("t_vs_t", "테테전", "tvstboard"),
            new Entry("z_vs_t", "저테전", "zvstboard"),
            new Entry("z_vs_p", "저프전", "zvspboard"),
            new Entry("z_vs_z", "저저전", "zvszboard"),
            new Entry("p_vs_t", "프테전", "pvstboard"),
            new Entry("p_vs_z", "프저전", "pvszboard"),
            new Entry("p_vs_p", "프프전", "pvspboard"),
            new Entry("honey_tip", "꿀팁", "tipboard"),
            new Entry("team_play", "팀플", "teamplayguideboard")
    ));

    private static final Map<String, Entry> BY_CATEGORY;

    static {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            entries.put(entry.getCategory(), entry);
        }
        BY_CATEGORY = Collections.unmodifiableMap(entries);
    }

    private StrategyTipSourceCatalog() {
    }

    public static List<Entry> entries() {
        return new ArrayList<>(ENTRIES);
    }

    public static Entry find(String category) {
        return category == null ? null : BY_CATEGORY.get(category);
    }

    public static boolean supports(String category) {
        return find(category) != null;
    }

    public static final class Entry {
        private final String category;
        private final String categoryName;
        private final String boardTitle;

        private Entry(String category, String categoryName, String boardTitle) {
            this.category = category;
            this.categoryName = categoryName;
            this.boardTitle = boardTitle;
        }

        public String getCategory() {
            return category;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public String getBoardTitle() {
            return boardTitle;
        }
    }
}
