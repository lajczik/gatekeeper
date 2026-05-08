package xyz.lychee.gatekeeper.shared.charts;

import com.grack.nanojson.JsonObject;

import java.util.Map;
import java.util.function.Supplier;

public class MultiLineChart extends CustomChart {

    private final Supplier<Map<String, Integer>> supplier;

    /**
     * Class constructor.
     *
     * @param chartId  The id of the chart.
     * @param supplier The supplier which is used to request the chart data.
     */
    public MultiLineChart(String chartId, Supplier<Map<String, Integer>> supplier) {
        super(chartId);
        this.supplier = supplier;
    }

    @Override
    protected Object getChartData() {
        JsonObject valuesBuilder = new JsonObject();

        Map<String, Integer> map = supplier.get();
        if (map == null || map.isEmpty()) {
            // Null = skip the chart
            return null;
        }
        boolean allSkipped = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() == 0) {
                continue; // Skip this invalid
            }
            allSkipped = false;
            valuesBuilder.put(entry.getKey(), entry.getValue());
        }
        if (allSkipped) {
            // Null = skip the chart
            return null;
        }
        return valuesBuilder;
    }

}