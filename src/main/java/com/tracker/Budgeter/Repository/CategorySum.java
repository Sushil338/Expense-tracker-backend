package com.tracker.Budgeter.Repository;

/**
 * Projection interface to handle the aggregated chart data.
 * Spring will automatically map the query aliases ('category' and 'total')
 * to these getter methods.
 */
public interface CategorySum {
    String getCategory();
    Double getTotal();
}
