package org.example;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.Queue;

public class Token<T> implements Serializable {
    Queue<Byte> queue;
    int [] lastRequestNumbers;
    T value;
    Token(){
        queue = new LinkedList<>();
        lastRequestNumbers = new int[]{0,0,0,0};
        value = null;
    }
}
