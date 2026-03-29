package org.example.data.tagdata;

public class RssiTagData extends BasicTagData{
    public final double rssi;

    public RssiTagData(String epc, String tid, double rssi) {
        super(epc, tid);
        this.rssi = rssi;
    }
    public RssiTagData(String epc, double rssi) {
        super(epc);
        this.rssi = rssi;
    }
    public RssiTagData(double rssi) {
        this.rssi = rssi;
    }


    public double getRssi() {
        return rssi;
    }

}
