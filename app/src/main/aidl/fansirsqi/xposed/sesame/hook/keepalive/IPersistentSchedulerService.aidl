package fansirsqi.xposed.sesame.hook.keepalive;

interface IPersistentSchedulerService {
    String registerSchedule(String scheduleJson, long nowMillis);
    void replaceWakeSchedules(String schedulesJson, long nowMillis);
    boolean acknowledge(String dedupeKey, long generation, long nowMillis);
    boolean cancel(String dedupeKey, long nowMillis);
    String reconcile(long nowMillis);
    String getSchedule(String dedupeKey);
}
