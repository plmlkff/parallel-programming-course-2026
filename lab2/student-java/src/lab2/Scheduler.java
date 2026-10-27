package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public final class Scheduler {
    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();
    private static final BooleanSupplier READY = () -> true;

    private final List<Worker> workers = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore returned = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    private boolean started;
    private boolean aborted;

    private static final class Worker {
        final Scheduler scheduler;
        final int id;
        final Runnable body;
        final Semaphore turn = new Semaphore(0);
        Thread thread;
        BooleanSupplier ready = READY;
        boolean touched = true;
        boolean finished;
        Throwable failure;

        Worker(Scheduler scheduler, int id, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.body = body;
        }
    }

    private static final class AbortRun extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AbortRun() { super(null, null, false, false); }
    }

    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        if (started) throw new IllegalStateException("spawn after run started");
        workers.add(new Worker(this, workers.size(), Objects.requireNonNull(body)));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        if (started) throw new IllegalStateException("Scheduler supports one run");
        Objects.requireNonNull(strategy);
        started = true;
        try {
            for (Worker w : workers) {
                w.thread = Thread.ofVirtual().name("lab2-" + letter(w.id))
                        .unstarted(() -> execute(w));
                w.thread.start();
                returned.acquireUninterruptibly();
                checkFailure(w);
            }

            int last = -1;
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean live = false;
                for (Worker w : workers) {
                    if (!w.finished) {
                        live = true;
                        if (w.ready.getAsBoolean()) enabled.add(w.id);
                    }
                }
                if (!live) break;
                if (enabled.isEmpty()) {
                    StringJoiner held = new StringJoiner(", ", "held={", "}");
                    for (SchedLock lock : locks) {
                        if (lock.owner >= 0) held.add(lock.name + ":" + letter(lock.owner));
                    }
                    deadlock = held.toString();
                    break;
                }

                int next = strategy.pick(enabled, last);
                if (!enabled.contains(next)) {
                    throw new IllegalArgumentException("strategy picked disabled thread " + next);
                }
                trace.append(letter(next));
                last = next;
                Worker w = workers.get(next);
                w.turn.release();
                returned.acquireUninterruptibly();
                checkFailure(w);
            }
        } finally {
            aborted = true;
            for (Worker w : workers) if (w.thread != null) w.turn.release();
            boolean interrupted = false;
            for (Worker w : workers) {
                if (w.thread == null) continue;
                while (w.thread.isAlive()) {
                    try { w.thread.join(); }
                    catch (InterruptedException e) { interrupted = true; }
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        current().touched = true;
        logOperation("window");
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        beforeOperation(Objects.requireNonNull(cond));
        logOperation("await done");
    }

    private void execute(Worker w) {
        CURRENT.set(w);
        try { w.body.run(); }
        catch (AbortRun ignored) { }
        catch (Throwable failure) { w.failure = failure; }
        finally {
            w.finished = true;
            CURRENT.remove();
            returned.release();
        }
    }

    private static void checkFailure(Worker w) {
        if (w.failure instanceof RuntimeException e) throw e;
        if (w.failure instanceof Error e) throw e;
        if (w.failure != null) throw new IllegalStateException("worker failed", w.failure);
    }

    private static Worker current() {
        Worker w = CURRENT.get();
        if (w == null) throw new IllegalStateException("operation outside Scheduler worker");
        if (w.scheduler.aborted) throw new AbortRun();
        return w;
    }

    static Scheduler currentScheduler() { return current().scheduler; }
    static int currentId() { return current().id; }
    static char letter(int id) { return (char) ('A' + id); }

    static void beforeOperation() { beforeOperation(READY); }

    static void beforeOperation(BooleanSupplier ready) {
        Worker w = current();
        if (w.touched || !ready.getAsBoolean()) {
            w.ready = ready;
            w.touched = false;
            w.scheduler.returned.release();
            w.turn.acquireUninterruptibly();
            if (w.scheduler.aborted) throw new AbortRun();
            w.ready = READY;
        }
        w.touched = true;
    }

    static void logOperation(String operation) {
        Worker w = current();
        if (w.scheduler.logging) w.scheduler.log.add(letter(w.id) + "  " + operation);
    }

    void register(SchedLock lock) {
        if (started) throw new IllegalStateException("create locks before run");
        locks.add(lock);
    }

    boolean isAborted() { return aborted; }
}
