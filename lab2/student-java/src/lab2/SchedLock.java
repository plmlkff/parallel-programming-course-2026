package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    private final Scheduler scheduler;
    int owner = -1;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        scheduler = s;
        s.register(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        checkScheduler();
        Scheduler.beforeOperation(() -> owner == -1);
        owner = Scheduler.currentId();
        Scheduler.logOperation("lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        if (scheduler.isAborted()) return;
        checkScheduler();
        Scheduler.beforeOperation();
        if (owner != Scheduler.currentId()) throw new IllegalStateException("not owner of " + name);
        owner = -1;
        Scheduler.logOperation("unlock " + name);
    }

    private void checkScheduler() {
        if (Scheduler.currentScheduler() != scheduler) {
            throw new IllegalStateException("lock belongs to another Scheduler");
        }
    }
}
