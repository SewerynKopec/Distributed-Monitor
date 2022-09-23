package org.example;

class RequestMessage {
    int sequenceNumber;
    int sequenceNumberValue;
    RequestMessage(byte [] bytes){
        sequenceNumber = bytes[0];
        for(int i = 1; i<5;++i){
            sequenceNumberValue <<= 8;
            sequenceNumberValue += bytes[i];
        }
    }
}
