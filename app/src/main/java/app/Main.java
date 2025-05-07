package app;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.GraphModel;
import model.TableType;
import sim.SimulationEngine;
import ui.GridEditor;
import ui.KitchenQueuePane;
import ui.RobotSimulationPane;

public class Main extends Application {

    private final GraphModel gm = new GraphModel();
    private final SimulationEngine sim = new SimulationEngine(gm);

    private MediaPlayer mediaPlayer;
    private ToggleButton muteBtn;
    
    // Tab references
    private TabPane tabs;
    private Tab tabLayout;
    private Tab tabKitchen;
    private Tab tabRobotSim;
    
    // Reference to the "Begin Simulation" button for enabling/disabling
    private Button beginSimButton;
    
    // Fields to store simulation times history
    private List<String> simulationTimes = new ArrayList<>();
    private int simulationRound = 0;

    // Add static instance reference to Main class
    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) {
        instance = this; // Set instance
        // Set stage title
        stage.setTitle("Restaurant Simulator");
        
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
        // Create scene with reasonable initial size - will be maximized after
        Scene scene = new Scene(wrapWithMute(startRoot), 800, 600);
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
        mediaPlayer.setVolume(1.5);
        mediaPlayer.play();

        // 4) Start button switches root to main UI
        startBtn.setOnAction(e -> {
            // 1) Create Fade out for startRoot
            FadeTransition fadeOut = new FadeTransition(Duration.millis(800), startRoot);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(evt -> {

                // 2) Create GridEditor + TabPane
                GridEditor edit = new GridEditor(gm, sim);
                Label status = new Label("พร้อมใช้งาน");
                edit.setStatusTarget(status);
                VBox topBar = new VBox(buildToolbar(edit), status);
                topBar.getStyleClass().add("tool-bar");
                BorderPane layoutRoot = new BorderPane(edit);
                layoutRoot.setTop(topBar);
                layoutRoot.getStyleClass().add("main-background");
                tabLayout = new Tab("Restaurant Layout", layoutRoot);
                tabLayout.setClosable(false);
                
                KitchenQueuePane kitchenPane = new KitchenQueuePane(sim);
                tabKitchen = new Tab("Kitchen & Robot", kitchenPane);
                tabKitchen.setClosable(false);
                
                // Create new Robot Simulation tab
                RobotSimulationPane robotSimPane = new RobotSimulationPane(sim);
                tabRobotSim = new Tab("Robot Simulation", robotSimPane);
                tabRobotSim.setClosable(false);
                
                // Create TabPane with all tabs
                tabs = new TabPane(tabLayout, tabKitchen, tabRobotSim);
                tabs.getStyleClass().add("tab-pane");
                tabs.setOpacity(0); // Start transparent for fade in

                // 3) Switch Scene root
                scene.setRoot(wrapWithMute(tabs));
                
                // Ensure window is on top after content change
                stage.setAlwaysOnTop(true);
                Platform.runLater(() -> {
                    stage.setAlwaysOnTop(false);
                    stage.toFront(); // Request focus and bring to front
                });
                
                // 4) Fade in new content
                FadeTransition fadeIn = new FadeTransition(Duration.millis(800), tabs);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            // Start fade out
            fadeOut.play();
        });

        // Register the simulation completion listener to re-enable the layout tab
        // and save simulation times
        sim.addSimulationCompletionListener(new SimulationEngine.SimulationCompletionListener() {
            @Override
            public void onSimulationComplete() {
                Platform.runLater(() -> {
                    // Re-enable the layout tab and Begin Simulation button
                    if (tabLayout != null) {
                        tabLayout.setDisable(false);
                    }
                    if (beginSimButton != null) {
                        beginSimButton.setDisable(false);
                    }
                    
                    // Bring window to front to show completion
                    stage.toFront();
                    
                    // Capture and store the simulation time
                    if (tabRobotSim != null && tabRobotSim.getContent() instanceof RobotSimulationPane) {
                        RobotSimulationPane robotPane = (RobotSimulationPane) tabRobotSim.getContent();
                        String currentTime = robotPane.getCurrentTimerText();
                        
                        // Store the time with the round number
                        simulationRound++;
                        String timeEntry = "Round " + simulationRound + ": " + currentTime;
                        simulationTimes.add(timeEntry);
                        
                        System.out.println("Saved simulation time: " + timeEntry);
                    }
                });
            }
        });

        // Set up the stage
        stage.setScene(scene);
        
        // Make the window maximized (not fullscreen)
        stage.setMaximized(true);
        
        // Make sure the window appears on top when first opened
        stage.setAlwaysOnTop(true);
        
        // Show the window
        stage.show();
        
        // After a short delay, disable always-on-top but keep window in foreground
        Platform.runLater(() -> {
            stage.setAlwaysOnTop(false);
            stage.toFront(); // Ensure window is in front
            stage.requestFocus(); // Give it focus
        });
    }

    public void resetSimulationHistory() {
        simulationTimes.clear();
        simulationRound = 0;
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
            makeTableBtn("K",   TableType.K  , ed, modeGroup),
            new Separator(),
            makePathToggle(ed, modeGroup),
            new Separator(),
            cancelDrawButton(ed, modeGroup),
            new Separator(),
            new Separator(),
            beginSim(ed),
            createShowTimesButton()
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
        btn.setOnAction(e -> {
            ed.setPathMode(btn.isSelected());
            ed.setCurrentTable(TableType.J);
        });
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

    private Button beginSim(GridEditor ed) {
        Button btn = new Button("Begin Simulation");
        // Store reference to the button for later enabling/disabling
        beginSimButton = btn;
        
        btn.setOnAction(e -> {
            // Call the original startSim method
            ed.startSim();
            
            // Disable the Restaurant Layout tab
            if (tabLayout != null) {
                tabLayout.setDisable(true);
            }
            
            // Switch to the Kitchen tab (index 1)
            if (tabs != null) {
                tabs.getSelectionModel().select(1);
            }
            
            // Disable the Begin Simulation button during simulation
            btn.setDisable(true);

            // Clear the order events in KitchenQueuePane
            clearOrderEvents();
        });
        return btn;
    }
    
    /**
     * Clears the order events in the KitchenQueuePane
     */
    private void clearOrderEvents() {
        // Find the KitchenQueuePane and clear its order log
        if (tabKitchen != null && tabKitchen.getContent() instanceof KitchenQueuePane) {
            KitchenQueuePane kitchenPane = (KitchenQueuePane) tabKitchen.getContent();
            kitchenPane.clearOrderLog();
        }
    }

    private Button createShowTimesButton() {
        Button btn = new Button("History");
        btn.setOnAction(e -> {
            if (simulationTimes.isEmpty()) {
                // Show message if no history yet
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Simulation History");
                alert.setHeaderText("No simulation history");
                alert.setContentText("You haven't completed any simulations yet.");
                alert.showAndWait();
            } else {
                showSimulationTimesSummary();
            }
        });
        return btn;
    }
    
    private void showSimulationTimesSummary() {
        // Create a dialog to show all simulation times
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Simulation History");
        
        VBox dialogRoot = new VBox(10);
        dialogRoot.setPadding(new Insets(20));
        dialogRoot.setAlignment(Pos.CENTER);
        
        Label titleLabel = new Label("Simulation Round Times");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        String averageTime = calculateAverageTime();
        Label averageLabel = new Label("Average Time: " + averageTime);
        averageLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        VBox timesList = new VBox(5);
        for (String timeEntry : simulationTimes) {
            Label timeLabel = new Label(timeEntry);
            timesList.getChildren().add(timeLabel);
        }
        
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> dialogStage.close());
        
        ScrollPane scrollPane = new ScrollPane(timesList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(150);
        
        dialogRoot.getChildren().addAll(titleLabel, averageLabel, scrollPane, closeButton);
        
        Scene dialogScene = new Scene(dialogRoot, 300, 250);
        dialogStage.setScene(dialogScene);
        dialogStage.show();
    }

    private String calculateAverageTime() {
        if (simulationTimes.isEmpty()) {
            return "00:00";
        }
        
        int totalSeconds = 0;
        int count = 0;
        
        for (String timeEntry : simulationTimes) {
            // Extract the time portion (MM:SS) from the entry
            // Format is "Round X: MM:SS"
            String timePart = timeEntry.substring(timeEntry.indexOf(":") + 2);
            
            // Split into minutes and seconds
            String[] parts = timePart.split(":");
            if (parts.length == 2) {
                try {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = Integer.parseInt(parts[1]);
                    totalSeconds += (minutes * 60) + seconds;
                    count++;
                } catch (NumberFormatException e) {
                    // Skip this entry if it can't be parsed
                    System.err.println("Could not parse time: " + timePart);
                }
            }
        }
        
        // Calculate and format average
        int avgSeconds = totalSeconds / count;
        int avgMinutes = avgSeconds / 60;
        int avgRemainingSeconds = avgSeconds % 60;
        
        return String.format("%02d:%02d", avgMinutes, avgRemainingSeconds);
    }

    public static void main(String[] args) {
        launch();
    }
}