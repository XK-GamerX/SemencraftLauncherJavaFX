module com.semencraft.semencraftlauncherjavafx {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.semencraft.semencraftlauncherjavafx to javafx.fxml;
    exports com.semencraft.semencraftlauncherjavafx;
}