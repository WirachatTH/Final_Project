package app;

import java.net.URL;
import java.util.Random;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import model.Dish;
import model.GraphModel;
import model.TableType;
import sim.SimulationEngine;
import ui.GridEditor;
import ui.KitchenQueuePane;

public class Main extends Application {

    private final SimulationEngine sim = new SimulationEngine();
    private final GraphModel       gm  = new GraphModel();

    private MediaPlayer mediaPlayer;
    private ToggleButton muteBtn;

    @Override
    public void start(Stage stage) {
        // 1) Build Start Screen
        VBox startRoot = new VBox(20);
        startRoot.getStyleClass().add("start-screen");
        startRoot.setAlignment(Pos.CENTER);

        Label title = new Label("🍜 Restaurant Simulator 🍜");
        title.getStyleClass().add("start-title");

        Button startBtn = new Button("Start");
        startBtn.getStyleClass().add("start-button");

        startRoot.getChildren().addAll(title, startBtn);

        // 2) Shared Scene + CSS
        Scene scene = new Scene(wrapWithMute(startRoot));
        URL css = getClass().getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        // 3) Setup background music
        String musicPath = getClass()
          .getResource("/mao zedong propaganda music Red Sun in the Sky 4.mp3")
          .toExternalForm();
        mediaPlayer = new MediaPlayer(new Media(musicPath));
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        mediaPlayer.setVolume(0.04);
        mediaPlayer.play();

        // 4) Start button switches root to main UI
        startBtn.setOnAction(e -> {

            // build GridEditor + status
            GridEditor edit = new GridEditor(gm);
            Label status = new Label("พร้อมใช้งาน");
            edit.setStatusTarget(status);

            // toolbar + status bar
            VBox topBar = new VBox(buildToolbar(edit), status);
            topBar.getStyleClass().add("tool-bar");

            // main layout
            BorderPane layoutRoot = new BorderPane(edit);
            layoutRoot.setTop(topBar);
            layoutRoot.getStyleClass().add("main-background");

            Tab tabLayout = new Tab("Restaurant Layout", layoutRoot);
            tabLayout.setClosable(false);

            KitchenQueuePane kitchenPane = new KitchenQueuePane(sim);
            Tab tabKitchen = new Tab("Kitchen & Robot", kitchenPane);
            tabKitchen.setClosable(false);

            TabPane tabs = new TabPane(tabLayout, tabKitchen);
            tabs.getStyleClass().add("tab-pane");

            // reuse the same scene, just swap its root
            scene.setRoot(wrapWithMute(tabs));
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();
    }

    /** Wraps any content in a StackPane with the mute button in bottom-right. */
    private Parent wrapWithMute(Parent content) {
        if (muteBtn == null) {
            // create and configure it once
            ToggleGroup tg = new ToggleGroup();
            muteBtn = new ToggleButton("🔇");
            muteBtn.setToggleGroup(tg);
            configureToggleButton(muteBtn);
            muteBtn.setOnAction(e -> toggleMute());
        }

        StackPane wrapper = new StackPane(content, muteBtn);
        StackPane.setAlignment(muteBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(muteBtn, new Insets(10));
        return wrapper;
    }

    private void toggleMute() {
        if (mediaPlayer != null) {
            MediaPlayer.Status status = mediaPlayer.getStatus();
            if (status == MediaPlayer.Status.PAUSED) {
                mediaPlayer.play();
                muteBtn.setText("🔈" );
            } else {
                mediaPlayer.pause();
                muteBtn.setText("🔇" );
            }
        }
    }

    private HBox buildToolbar(GridEditor ed) {
        ToggleGroup modeGroup = new ToggleGroup();
        return new HBox(8,
            makeTableBtn("T2",  TableType.T2 , ed, modeGroup),
            makeTableBtn("T4",  TableType.T4 , ed, modeGroup),
            makeTableBtn("T6",  TableType.T6 , ed, modeGroup),
            makeTableBtn("T8",  TableType.T8 , ed, modeGroup),
            makeTableBtn("T10", TableType.T10, ed, modeGroup),
            new Separator(),
            makePathToggle(ed, modeGroup),
            new Separator(),
            cancelDrawButton(ed, modeGroup)
        );
    }

    private void configureToggleButton(ToggleButton btn) {
        btn.setStyle(
          "-fx-focus-color: transparent;" +
          "-fx-faint-focus-color: transparent;" +
          "-fx-border-color: transparent;" +
          "-fx-border-width: 0;"
        );
        btn.selectedProperty().addListener((obs, old, sel) -> {
            if (sel) {
                btn.setStyle("-fx-border-color: #0096c9; -fx-border-width: 2px;");
            } else {
                btn.setStyle(
                  "-fx-focus-color: transparent;" +
                  "-fx-faint-focus-color: transparent;" +
                  "-fx-border-color: transparent;" +
                  "-fx-border-width: 0;"
                );
            }
        });
    }

    private ToggleButton makeTableBtn(String text, TableType t,
                                      GridEditor ed, ToggleGroup g) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(g);
        configureToggleButton(btn);
        btn.setOnAction(e -> {
            ed.setPathMode(false);
            ed.setCurrentTable(t);
        });
        return btn;
    }

    private ToggleButton makePathToggle(GridEditor ed, ToggleGroup g) {
        ToggleButton btn = new ToggleButton("Path");
        btn.setToggleGroup(g);
        configureToggleButton(btn);
        btn.setOnAction(e -> ed.setPathMode(btn.isSelected()));
        return btn;
    }

    private Button cancelDrawButton(GridEditor ed, ToggleGroup g) {
        Button btn = new Button("Cancel Drawing");
        btn.setOnAction(e -> {
            ed.cancelDraw();
            g.selectToggle(null);
        });
        return btn;
    }

    public static void main(String[] args) {
        launch();
    }
}
