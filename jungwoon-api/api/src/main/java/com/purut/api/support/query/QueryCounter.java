package com.purut.api.support.query;

/**
 * 요청 하나가 쓴 쿼리 수·시간을 센다.
 *
 * N+1 은 결과가 정확해서 눈으로는 안 보인다. 데이터가 적을 땐 빠르기까지 하다.
 * "이 화면이 쿼리를 몇 번 쓰는가"를 상시로 보여줘야 늘어나는 순간에 알아챈다.
 *
 * p6spy 에 의존하지 않는다 — 운영 jar 에는 p6spy 가 없어도 이 클래스는 로드된다.
 */
public final class QueryCounter {

    private static final ThreadLocal<Stat> CURRENT = new ThreadLocal<>();

    private QueryCounter() {
    }

    public static void start() {
        CURRENT.set(new Stat());
    }

    public static void record(long elapsedNanos) {
        Stat stat = CURRENT.get();
        if (stat != null) {
            stat.count++;
            stat.totalNanos += elapsedNanos;
        }
    }

    /** 집계를 끝내고 결과를 돌려준다. 호출 후 스레드에 남기지 않는다. */
    public static Stat finish() {
        Stat stat = CURRENT.get();
        CURRENT.remove();
        return stat;
    }

    public static final class Stat {
        private int count;
        private long totalNanos;

        public int count() {
            return count;
        }

        public long elapsedMillis() {
            return totalNanos / 1_000_000;
        }
    }
}
