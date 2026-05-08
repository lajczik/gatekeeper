package xyz.lychee.gatekeeper.shared.charts;

import com.grack.nanojson.JsonObject;

public abstract class CustomChart {

    private final String chartId;

    protected CustomChart(String chartId) {
        if (chartId == null) {
            throw new IllegalArgumentException("chartId must not be null");
        }
        this.chartId = chartId;
    }

    public JsonObject getRequestJsonObject() {
        JsonObject builder = new JsonObject();
        builder.put("chartId", chartId);
        try {
            Object data = getChartData();
            if (data == null) {
                // If the data is null we don't send the chart.
                return null;
            }
            JsonObject values = new JsonObject();
            values.put("values", data);
            builder.put("data", values);
        } catch (Throwable t) {
            return null;
        }
        return builder;
    }

    protected abstract Object getChartData();
}
