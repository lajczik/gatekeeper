package xyz.lychee.gatekeeper.shared.charts;

import java.util.function.Supplier;

public class SimplePie extends CustomChart {

    private final Supplier<String> supplier;

    /**
     * Class constructor.
     *
     * @param chartId  The id of the chart.
     * @param supplier The supplier which is used to request the chart data.
     */
    public SimplePie(String chartId, Supplier<String> supplier) {
        super(chartId);
        this.supplier = supplier;
    }

    @Override
    protected Object getChartData() {
        String value = supplier.get();
        if (value == null || value.isEmpty()) {
            // Null = skip the chart
            return null;
        }
        return value;
    }
}