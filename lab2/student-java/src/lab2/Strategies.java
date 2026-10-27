package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position == trace.length()) {
                throw new IllegalArgumentException("replay exhausted at decision " + (position + 1));
            }
            int next = trace.charAt(position++) - 'A';
            if (!enabled.contains(next)) {
                throw new IllegalArgumentException("replay: thread " + trace.charAt(position - 1)
                        + " is not enabled at decision " + position);
            }
            return next;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random random;

        public RandomPick(long seed) { random = new Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(random.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private final List<Choice> path = new ArrayList<>();
        private int position;
        private int preemptions;

        private static final class Choice {
            final List<Integer> options;
            int selected;

            Choice(List<Integer> options) { this.options = options; }
        }

        public Dfs(int bound) {
            if (bound < 0) throw new IllegalArgumentException("bound must be nonnegative");
            this.bound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() { position = 0; preemptions = 0; }

        public int pick(List<Integer> enabled, int last) {
            boolean lastEnabled = enabled.contains(last);
            if (position == path.size()) {
                List<Integer> options = new ArrayList<>();
                for (int next : enabled) {
                    if (next == last || !lastEnabled || preemptions < bound) options.add(next);
                }
                path.add(new Choice(options));
            }
            Choice choice = path.get(position++);
            int next = choice.options.get(choice.selected);
            if (lastEnabled && next != last) preemptions++;
            return next;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            path.subList(position, path.size()).clear();
            while (!path.isEmpty()) {
                Choice choice = path.get(path.size() - 1);
                if (++choice.selected < choice.options.size()) return true;
                path.remove(path.size() - 1);
            }
            return false;
        }
    }
}
