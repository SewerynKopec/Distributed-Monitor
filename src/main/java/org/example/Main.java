package org.example;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        switch (args[0]) {
            case "p1" -> {
                System.out.println("Starting p1...");
                new Producer().producer(new String[]{"tcp://127.0.0.1:9000", "tcp://127.0.0.1:9001", "tcp://127.0.0.1:9002", "tcp://127.0.0.1:9003"}, 0, true, 0, 1000);
            }
            case "p2" -> {
                System.out.println("Starting p2...");
                new Producer().producer(new String[]{"tcp://127.0.0.1:9001", "tcp://127.0.0.1:9000", "tcp://127.0.0.1:9002", "tcp://127.0.0.1:9003"}, 1, false, 100, 1000);
            }
            case "c1" -> {
                System.out.println("Starting c1...");
                new Consumer().consumer(new String[]{"tcp://127.0.0.1:9002", "tcp://127.0.0.1:9000", "tcp://127.0.0.1:9001", "tcp://127.0.0.1:9003"}, 2, false, 2000);
            }
            case "c2" -> {
                System.out.println("Starting c2...");
                new Consumer().consumer(new String[]{"tcp://127.0.0.1:9003", "tcp://127.0.0.1:9000", "tcp://127.0.0.1:9001", "tcp://127.0.0.1:9002"}, 3, false, 3000);
            }
        }
    }
}
