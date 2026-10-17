package com.miaokatze.gtfi.util;

import java.util.ArrayList;
import java.util.List;

/** Keep external item details in the state section and the complete brand footer last. */
public final class DeviceTooltipOrder {

    private DeviceTooltipOrder() {}

    public static void normalize(List<String> lines) {
        int state = lines.indexOf(DeviceTooltip.sectionTitle("state"));
        int description = lines.indexOf(DeviceTooltip.sectionTitle("description"));
        int controls = lines.indexOf(DeviceTooltip.sectionTitle("controls"));
        String footer = DeviceTooltip.addedByLine();
        int end = lines.indexOf(footer);
        if (state < 0 || description <= state
            || controls <= description
            || end <= controls
            || end != lines.lastIndexOf(footer)) return;

        List<String> details = new ArrayList<String>();
        for (int i = end + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) details.add(line);
        }
        lines.subList(end, lines.size())
            .clear();
        int insertion = description;
        while (insertion > state + 1 && lines.get(insertion - 1)
            .isEmpty()) insertion--;
        lines.addAll(insertion, details);
        if (!lines.get(lines.size() - 1)
            .isEmpty()) lines.add("");
        lines.add(footer);
        for (int i = lines.size() - 2; i > state; i--) {
            if (lines.get(i)
                .isEmpty()
                && lines.get(i - 1)
                    .isEmpty())
                lines.remove(i);
        }
    }
}
