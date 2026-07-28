package com.jungwoon.api.support.query;

import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.SimpleJdbcEventListener;

/**
 * p6spy 가 쿼리를 실행할 때마다 {@link QueryCounter} 에 알린다.
 *
 * ServiceLoader 로 등록된다 (META-INF/services/com.p6spy.engine.event.JdbcEventListener).
 * p6spy 가 클래스패스에 없는 운영 환경에서는 이 파일이 읽히지 않아 아무 일도 일어나지 않는다.
 */
public class P6SpyQueryCountListener extends SimpleJdbcEventListener {

    @Override
    public void onAfterExecuteQuery(PreparedStatementInformation information, long timeElapsedNanos,
                                    java.sql.SQLException e) {
        QueryCounter.record(timeElapsedNanos);
    }

    @Override
    public void onAfterExecuteUpdate(PreparedStatementInformation information, long timeElapsedNanos,
                                     int rowCount, java.sql.SQLException e) {
        QueryCounter.record(timeElapsedNanos);
    }

    @Override
    public void onAfterExecute(StatementInformation information, long timeElapsedNanos,
                               String sql, java.sql.SQLException e) {
        QueryCounter.record(timeElapsedNanos);
    }
}
