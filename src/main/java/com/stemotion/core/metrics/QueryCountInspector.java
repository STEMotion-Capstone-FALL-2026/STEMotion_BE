package com.stemotion.core.metrics;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Counts the SQL statements Hibernate prepares on the current thread, so each request can log how
 * many database round trips it made (DB performance plan, step 1). The SQL itself is untouched and
 * never logged.
 */
public class QueryCountInspector implements StatementInspector {

    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[1]);

    @Override
    public String inspect(String sql) {
        COUNT.get()[0]++;
        return sql;
    }

    public static void reset() {
        COUNT.get()[0] = 0;
    }

    public static int count() {
        return COUNT.get()[0];
    }
}
