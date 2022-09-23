package org.example;

import static java.lang.Thread.sleep;

public class Producer {
    public void producer(String[] args, int sequenceNumber, boolean hasToken, int offset, int workTime) throws InterruptedException {
        Monitor<Double> monitor = new Monitor<>(args, sequenceNumber, hasToken);
        System.out.println("Producer started");
        Double buf;
        for(int i=1;i<=100;++i){
            buf = monitor.acquire();
            if(buf == null) buf = (double)-1;
            System.out.println();
            System.out.println("Entering critical section.");
            //critical section
            System.out.println("");
            while(buf != -1) {
                buf = monitor.hold();
                System.out.println("Resumed");
            }
            buf = (double) (i + offset);
            buf /= 3;
            System.out.println("Producing: " + buf);
            sleep(workTime);
            monitor.resumeAll();
            //end of critical section
            monitor.release(buf);
        }
        monitor.close();
    }
}
