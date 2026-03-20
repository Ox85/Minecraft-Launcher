module spicy.launcher {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.net.http;
    requires com.google.gson;
    requires jdk.httpserver;
    requires java.desktop;


    opens spicy.launcher.ui to javafx.fxml;
    opens spicy.launcher.model to com.google.gson;
    opens spicy.launcher.core to com.google.gson;
    exports spicy.launcher.ui;
    exports spicy.launcher.core;
}