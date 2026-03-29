package org.example.data.tagdata;


public class MyTagData extends BasicTagData{
    public double rssi = 0;
    public double bpi = 0;

    public MyTagData(String epc, String tid){
        super(epc, tid);
    }
    public MyTagData(String epc){
        super(epc);
    }
    public MyTagData(){
        super();
    }

    public MyTagData(String epc, String tid,
                     double rssi,
                     double bpi){
        super(epc, tid);
        this.rssi = rssi;
        this.bpi = bpi;
    }
    public MyTagData(String epc,
                     double rssi,
                     double bpi){
        super(epc);
        this.rssi = rssi;
        this.bpi = bpi;
    }
    public MyTagData(
                     double rssi,
                     double bpi){
        super();
        this.rssi = rssi;
        this.bpi = bpi;
    }
}
