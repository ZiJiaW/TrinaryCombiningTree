import java.util.concurrent.atomic.AtomicBoolean;

class testThread extends Thread {
    int threadId;
    CombinationCounter counter;
    AtomicBoolean[] records;
    int testCount;
    testThread(int id, CombinationCounter c, AtomicBoolean[] rcs, int tc) {
        threadId = id;
        counter = c;
        records = rcs;
        testCount = tc;
    }

    @Override
    public void run() {
        for (int i = 0; i < testCount; ++i) {
            counter.getAndIncrement(threadId);
            if (records[threadId*testCount+i].compareAndSet(false, true))
                continue;
            else {
                System.out.println("Error: Duplicate output!");
                System.exit(-100);
            }
        }
        System.out.printf("No duplicate count in thread %d!\n", threadId);
    }
}

public class Main {
    static int getLeafNum(int depth) {
        int res = 1;
        for (int i = 0; i < depth - 1; ++i) res *= 3;
        return res;
    }

    public static void main(String[] args) {
        int depth = 4;
        int leafNum = getLeafNum(depth);
        int testCountPerThread = 100;
        int recordsLength = testCountPerThread * leafNum;
        CombinationCounter counter = new CombinationCounter(depth);
        AtomicBoolean[] records = new AtomicBoolean[recordsLength];
        for (int i = 0; i <recordsLength; i++) {
            records[i] = new AtomicBoolean(false);
        }

        testThread[] ts = new testThread[leafNum];
        for (int i = 0; i < leafNum; i++) {
            ts[i] = new testThread(i, counter, records, testCountPerThread);
            ts[i].start();
        }
        for (testThread t:ts) {
            try {
                t.join();
            } catch (InterruptedException ex) {
                //..
            }
        }
        for (int i = 0; i < recordsLength; i++) {
            if (!records[i].get())
                System.out.println("Error: Count omission!");
        }
        System.out.println("No omitted count!");
        System.out.println("Test finished without error!");
    }
}
