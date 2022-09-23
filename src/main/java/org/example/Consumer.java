package org.example;

import static java.lang.Thread.sleep;

public class Consumer {
    public void consumer(String[] args, int sequenceNumber, boolean hasToken, int workTime) throws InterruptedException {
        Monitor<Double> monitor = new Monitor<>(args,sequenceNumber, hasToken);
        Double buf;
        for(int i=1;i<=100;++i){
            buf = monitor.acquire();
            System.out.println();
            System.out.println("Entering critical section.");
            while(buf == -1) {
                System.out.println("HOLD");
                buf = monitor.hold();
                System.out.println("Resumed");
            }
            System.out.println("Consuming: " + buf);
            buf = (double) -1;
            sleep(workTime);
            monitor.resumeAll();
            System.out.println("Leaving critical section.");
            monitor.release(buf);
        }
        monitor.close();
    }
}
