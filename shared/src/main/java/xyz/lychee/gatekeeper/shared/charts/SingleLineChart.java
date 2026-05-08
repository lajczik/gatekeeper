package xyz.lychee.gatekeeper.shared.charts;

import java.util.function.Supplier;

public class SingleLineChart extends CustomChart {

    private final Supplier<Integer> supplier;

    /**
     * Class constructor.
     *
     * @param chartId  The id of the chart.
     * @param supplier The supplier which is used to request the chart data.
     */
    public SingleLineChart(String chartId, Supplier<Integer> supplier) {
        super(chartId);
        this.supplier = supplier;
    }

    @Override
    protected Object getChartData() {
        int value = supplier.get();
        if (value == 0) {
            // Null = skip the chart
            return null;
        }
        return value;
    }
}