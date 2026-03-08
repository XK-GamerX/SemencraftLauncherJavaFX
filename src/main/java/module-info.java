module com.semencraft.semencraftlauncherjavafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.net.http;
    requires com.google.gson;

    opens com.semencraft.semencraftlauncherjavafx to javafx.fxml;
    exports com.semencraft.semencraftlauncherjavafx;
}
