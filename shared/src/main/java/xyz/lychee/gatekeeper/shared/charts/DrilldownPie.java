package xyz.lychee.gatekeeper.shared.charts;

import com.grack.nanojson.JsonObject;

import java.util.Map;
import java.util.function.Supplier;

public class DrilldownPie extends CustomChart {

    private final Supplier<Map<String, Map<String, Integer>>> supplier;

    /**
     * Class constructor.
     *
     * @param chartId  The id of the chart.
     * @param supplier The supplier which is used to request the chart data.
     */
    public DrilldownPie(String chartId, Supplier<Map<String, Map<String, Integer>>> supplier) {
        super(chartId);
        this.supplier = supplier;
    }

    @Override
    public Object getChartData() {
        JsonObject valuesBuilder = new JsonObject();

        Map<String, Map<String, Integer>> map = supplier.get();
        if (map == null || map.isEmpty()) {
            // Null = skip the chart
            return null;
        }
        boolean reallyAllSkipped = true;
        for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
            JsonObject valueBuilder = new JsonObject();
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
                valueBuilder.put(valueEntry.getKey(), valueEntry.getValue());
                allSkipped = false;
            }
            if (!allSkipped) {
                reallyAllSkipped = false;
                valuesBuilder.put(entryValues.getKey(), valueBuilder);
            }
        }
        if (reallyAllSkipped) {
            // Null = skip the chart
            return null;
        }
        return valuesBuilder;
    }
}