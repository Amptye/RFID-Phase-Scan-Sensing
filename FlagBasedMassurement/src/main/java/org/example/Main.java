package org.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

import org.example.reader.ReaderSettings;
import org.example.web.WebSocketServer;

public class Main {
    public static void main(String[] args) {
        ReaderSettings.main(args);
        WebSocketServer.main(args);
    }
}
