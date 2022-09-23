package org.example;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import static java.lang.Thread.sleep;

public class Monitor<T> {
    ZContext context;
    ZMQ.Socket pubSocket, subSocket;
    int sequenceNumber;
    int numOfDevices;
    String [] URIs;
    String pubURI;
    int [] requestNumbers;
    boolean[] received;
    boolean hasToken;
    boolean inCriticalSection = false;
    boolean onHold = false;
    boolean connectionEstablished = false;
    boolean closed = false;
    Token<T> token;
    List<Byte> unconfirmedConnections;
    BlockingQueue<Integer> tokenQueue, holdQueue;
    BlockingQueue<byte[]> connectionQueue;
    Monitor(String[] URIs, int sequenceNumber, boolean hasToken){
        this.numOfDevices = URIs.length;
        this.pubURI = URIs[0];
        this.URIs = URIs;
        this.requestNumbers = new int[numOfDevices];
        this.hasToken = hasToken;
        this.sequenceNumber = sequenceNumber;
        this.received = new boolean[numOfDevices];
        this.token = new Token<>();
        this.tokenQueue = new LinkedBlockingQueue<>();
        this.holdQueue = new LinkedBlockingQueue<>();
        this.connectionQueue = new LinkedBlockingQueue<>();
        this.unconfirmedConnections = new LinkedList<>();
        setSockets();
        new Thread(this::listen).start();
        waitToConnect();
    }
    private void setSockets(){
        context = new ZContext(1);
        pubSocket = context.createSocket(SocketType.PUB);
        pubSocket.bind(pubURI);
        subSocket = context.createSocket(SocketType.SUB);
        subSocket.subscribe("".getBytes());
        for (String uri : URIs) {
            if(!uri.equals(pubURI))
                if(!subSocket.connect(uri))
                    System.out.println("Didn't connect to" + uri);
                else
                    System.out.println("Connected to " + uri);
        }
    }
    private void waitToConnect() {
        new Thread(()->{
            while(!connectionEstablished) {
                try {
                    pubSocket.send(new byte[]{(byte) sequenceNumber, (byte) sequenceNumber});
                    System.out.println("Sending ping.");
                    sleep(4000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }).start();
        try{
            //until everybody responds, listen
            for(int i=0;i<numOfDevices-1;++i)
                connectionQueue.take();
        }
        catch (InterruptedException e){
            System.out.println(e.getLocalizedMessage());
        }
        System.out.println("SYNCHRONIZED");
        System.out.println();
        System.out.println();
        connectionEstablished = true;
    }
    public T acquire() throws InterruptedException {
        if(!hasToken){
            ++requestNumbers[sequenceNumber];
            byte[] requestNumber = ByteBuffer.allocate(4).putInt(requestNumbers[sequenceNumber]).array();
            byte[] bytes = {(byte) sequenceNumber,  requestNumber[0], requestNumber[1], requestNumber[2], requestNumber[3]};
            pubSocket.send(bytes);
            tokenQueue.take();
        }
        inCriticalSection = true;
        hasToken = true;
        onHold = false;
        return token.value;
    }
    public Token<T> deserialize(byte[] bytes) {
        InputStream is = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(is)) {
            return (Token<T>) ois.readObject();
        } catch (IOException | ClassNotFoundException ioe) {
            ioe.printStackTrace();
        }
        throw new RuntimeException();
    }
    private byte[] serialize(Token<T> token) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream ois = new ObjectOutputStream(baos)) {
            ois.writeObject(token);
            return baos.toByteArray();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        throw new RuntimeException();
    }
    private void listen(){
        System.out.println("Starting listening");
        byte[] bytes = new byte[0];
        while(!closed){
            try {
                bytes = subSocket.recv();
            }
            catch (ClosedSelectorException | ZMQException e){
                System.out.println("Socket closed.");
            }
            readMessage(bytes);
        }
    }
    private void readMessage(byte [] bytes){
        assert bytes != null;
        //request message
        if(bytes.length == 5){
            RequestMessage message = new RequestMessage(bytes);
            if(requestNumbers[message.sequenceNumber] < message.sequenceNumberValue)
                requestNumbers[message.sequenceNumber] = message.sequenceNumberValue;
            if(onHold && token!= null && !token.queue.isEmpty()){
                try{
                    holdQueue.put(1);
                }
                catch (InterruptedException e){
                    System.out.println(e.getLocalizedMessage());
                }
            }
        }
        //synchronize message
        else if(bytes.length == 2){
            try{
                if(!received[bytes[0]] && bytes[1] == sequenceNumber) {
                    connectionQueue.put(bytes);
                    received[bytes[0]] = true;
                    System.out.println("Connection confirmed by " + bytes[0]);
                }
                else if(bytes[0] == bytes[1]){
                    bytes[0] = (byte) sequenceNumber;
                    pubSocket.send(bytes);
                }
            }
            catch (InterruptedException e){
                System.out.println(e.getLocalizedMessage());
            }

        }
        //notify message
        else if(bytes.length == 1) {
            if (onHold) {
                try{
                    holdQueue.put(1);
                } catch (InterruptedException e) {
                    System.out.println(e.getLocalizedMessage());
                }
            }
        }
        //token
        else{
            token = deserialize(bytes);
            if(token.queue.peek() == null || token.queue.peek() == sequenceNumber){
                if(token.queue.peek() != null)
                    token.queue.remove();
                try{
                    tokenQueue.put(1);
                }catch (InterruptedException e){
                    System.out.println(e.getLocalizedMessage());
                }
            }
        }
    }
    public synchronized T hold() throws InterruptedException {
        onHold = true;
        release(token.value);
        holdQueue.take();
        return acquire();

    }
    public void release(T value) {
        token.value = value;
        updateTokenNumbers();
        inCriticalSection = false;
        sendToken();
    }
    private void updateTokenNumbers(){
        token.lastRequestNumbers[sequenceNumber] = requestNumbers[sequenceNumber];
        for(byte i=0; i<4;++i){
            if(requestNumbers[i] == token.lastRequestNumbers[i]+1 && !token.queue.contains(i)){
                System.out.println("Adding " + i + " to the token.queue.");
                token.queue.add(i);
            }
        }
    }
    private synchronized void sendToken(){
        if(!token.queue.isEmpty()){
            if(token.queue.peek() != sequenceNumber) {
                System.out.println("Sending token to " + token.queue.peek());
                pubSocket.send(serialize(token));
                hasToken = false;
                token = null;
            }
            else{
                token.queue.remove();
            }
        }
    }
    public void resumeAll(){
        pubSocket.send(new byte[]{1});
    }
    public void close(){
        closed = true;
        subSocket.close();
        pubSocket.close();
    }
}