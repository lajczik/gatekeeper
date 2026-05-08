package xyz.lychee.gatekeeper.shared.charts;

import com.grack.nanojson.JsonObject;

import java.util.Map;
import java.util.function.Supplier;

public class SimpleBarChart extends CustomChart {

    private final Supplier<Map<String, Integer>> supplier;

    /**
     * Class constructor.
     *
     * @param chartId  The id of the chart.
     * @param supplier The supplier which is used to request the chart data.
     */
    public SimpleBarChart(String chartId, Supplier<Map<String, Integer>> supplier) {
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
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            valuesBuilder.put(entry.getKey(), new int[]{entry.getValue()});
        }
        return valuesBuilder;
    }

}