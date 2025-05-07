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

    private final GraphModel gm = new GraphModel(); //restaurant layout
    private final SimulationEngine sim = new SimulationEngine(gm); //simulate engine

    private MediaPlayer mediaPlayer; //background music player
    private ToggleButton muteBtn; //toggle music button
    
    private TabPane tabs; //tab pane containing all tabs
    private Tab tabLayout; //tab containing all editors
    private Tab tabKitchen; //the kitchen queue tab
    private Tab tabRobotSim; //the robot sim tab
    
    private Button beginSimButton;
    
    
    private List<String> simulationTimes = new ArrayList<>(); //history of run times
    private int simulationRound = 0; //total runs

    
    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) {
        instance = this; 
        
        stage.setTitle("Restaurant Simulator");
        
        
        VBox startRoot = new VBox(20);
        startRoot.getStyleClass().add("start-screen");
        startRoot.setAlignment(Pos.CENTER);

        Label title = new Label("🍜 Restaurant Simulator 🍜");
        title.getStyleClass().add("start-title");

        Button startBtn = new Button("Start");
        startBtn.getStyleClass().add("start-button");

        startRoot.getChildren().addAll(title, startBtn); //add the title label and start btn to the start screen

        
        
        Scene scene = new Scene(wrapWithMute(startRoot), 800, 600); //set default size
        URL css = getClass().getResource("/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm()); //link app.css to all files
        }

        
        String musicPath = getClass() //setting up the music player
          .getResource("/mao zedong propaganda music Red Sun in the Sky 4.mp3")
          .toExternalForm();
        mediaPlayer = new MediaPlayer(new Media(musicPath));
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
        mediaPlayer.setVolume(1.5);
        mediaPlayer.play();

        
        startBtn.setOnAction(e -> { //click on start btn
            
            FadeTransition fadeOut = new FadeTransition(Duration.millis(800), startRoot); //transition
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(evt -> {

                
                GridEditor edit = new GridEditor(gm, sim); //initiate the gridEditor
                Label status = new Label("พร้อมใช้งาน");
                edit.setStatusTarget(status);
                VBox topBar = new VBox(buildToolbar(edit), status);
                topBar.getStyleClass().add("tool-bar");
                BorderPane layoutRoot = new BorderPane(edit);
                layoutRoot.setTop(topBar);
                layoutRoot.getStyleClass().add("main-background");
                tabLayout = new Tab("Restaurant Layout", layoutRoot);
                tabLayout.setClosable(false);
                
                KitchenQueuePane kitchenPane = new KitchenQueuePane(sim); //initiate the kitchen queue tab
                tabKitchen = new Tab("Kitchen & Robot", kitchenPane);
                tabKitchen.setClosable(false);
                
                
                RobotSimulationPane robotSimPane = new RobotSimulationPane(sim); //initiate the robot sim tab
                tabRobotSim = new Tab("Robot Simulation", robotSimPane);
                tabRobotSim.setClosable(false);
                
                
                tabs = new TabPane(tabLayout, tabKitchen, tabRobotSim);
                tabs.getStyleClass().add("tab-pane");
                tabs.setOpacity(0); 

                
                scene.setRoot(wrapWithMute(tabs)); //put all tabs in the scene
                
                
                stage.setAlwaysOnTop(true); //make the application appears on top of the screen
                Platform.runLater(() -> {
                    stage.setAlwaysOnTop(false);
                    stage.toFront(); 
                });
                
                
                FadeTransition fadeIn = new FadeTransition(Duration.millis(800), tabs); //execute the transition
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            
            fadeOut.play();
        });

        
        
        sim.addSimulationCompletionListener(new SimulationEngine.SimulationCompletionListener() { //sim completion detecter
            @Override
            public void onSimulationComplete() {
                Platform.runLater(() -> {
                    
                    if (tabLayout != null) { //the user can go back to gridEditor tab again
                        tabLayout.setDisable(false);
                    }
                    if (beginSimButton != null) { //the user can now click the begin sim btn again
                        beginSimButton.setDisable(false);
                    }
                    
                    
                    stage.toFront();
                    
                    
                    if (tabRobotSim != null && tabRobotSim.getContent() instanceof RobotSimulationPane) { //save this round's data
                        RobotSimulationPane robotPane = (RobotSimulationPane) tabRobotSim.getContent();
                        String currentTime = robotPane.getCurrentTimerText();
                        
                        
                        simulationRound++;
                        String timeEntry = "Round " + simulationRound + ": " + currentTime;
                        simulationTimes.add(timeEntry);
                        
                        System.out.println("Saved simulation time: " + timeEntry);
                    }
                });
            }
        });

        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setAlwaysOnTop(true);
        stage.show();
        
        Platform.runLater(() -> {
            stage.setAlwaysOnTop(false);
            stage.toFront(); 
            stage.requestFocus(); 
        });
    }

    public void resetSimulationHistory() { //History reset when the reset btn is pressed
        simulationTimes.clear();
        simulationRound = 0;
    }

    private Parent wrapWithMute(Parent content) { //put the mute button in all tabs
        if (muteBtn == null) {
            
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

    private void toggleMute() { //a function to toggle the bg music
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

    private HBox buildToolbar(GridEditor ed) { //the tool bar in the gridEditor tab
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

    private void configureToggleButton(ToggleButton btn) { //apply the styles to all toggle buttons
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

    private ToggleButton makeTableBtn(String text, TableType t, GridEditor ed, ToggleGroup g) { //set "place table mode" when click on table btn, change the table type according to the btn, disable other modes
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(g);
        configureToggleButton(btn);
        btn.setOnAction(e -> {
            ed.setPathMode(false);
            ed.setCurrentTable(t);
        });
        return btn;
    }

    private ToggleButton makePathToggle(GridEditor ed, ToggleGroup g) { //set "place path mode" when click on a path btn, disable other modes
        ToggleButton btn = new ToggleButton("Path");
        btn.setToggleGroup(g);
        configureToggleButton(btn);
        btn.setOnAction(e -> {
            ed.setPathMode(btn.isSelected());
            ed.setCurrentTable(TableType.J);
        });
        return btn;
    }

    private Button cancelDrawButton(GridEditor ed, ToggleGroup g) { //a btn to stop a current path drawing process
        Button btn = new Button("Cancel Drawing");
        btn.setOnAction(e -> {
            ed.cancelDraw();
            g.selectToggle(null);
        });
        return btn;
    }

    private Button beginSim(GridEditor ed) { //begin sim btn
    Button btn = new Button("Begin Simulation");
    
    beginSimButton = btn;
    
    btn.setOnAction(e -> { //begin sim
        
        boolean validationSucceeded = ed.startSim();
        
        
        if (validationSucceeded) { //if all conditions passed, return true
            
            if (tabLayout != null) { //disable gridEditor tab so users can't change the grid during sim
                tabLayout.setDisable(true);
            }
            
            
            if (tabs != null) { //immediately jump to kitchenqueue tab
                tabs.getSelectionModel().select(1);
            }
            
            
            btn.setDisable(true); //disable begin sim btn during sim

            
            clearOrderEvents(); //clear the order events in past sims (if exist)
        }
        
        
    });
    return btn;
}
    
    private void clearOrderEvents() { //clear the order events
        
        if (tabKitchen != null && tabKitchen.getContent() instanceof KitchenQueuePane) {
            KitchenQueuePane kitchenPane = (KitchenQueuePane) tabKitchen.getContent();
            kitchenPane.clearOrderLog();
        }
    }

    private Button createShowTimesButton() { //history btn
        Button btn = new Button("History");
        btn.setOnAction(e -> {
            if (simulationTimes.isEmpty()) { //if no sim has been run yet
                
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
    
    private void showSimulationTimesSummary() { //show all run times and calculate the average time
        
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
        if (simulationTimes.isEmpty()) { //default if no sim has been run
            return "00:00";
        }
        
        int totalSeconds = 0;
        int count = 0;
        
        for (String timeEntry : simulationTimes) {
            
            
            String timePart = timeEntry.substring(timeEntry.indexOf(":") + 2);
            
            
            String[] parts = timePart.split(":"); //split minute and second from xx:yy format
            if (parts.length == 2) {
                try {
                    int minutes = Integer.parseInt(parts[0]);
                    int seconds = Integer.parseInt(parts[1]);
                    totalSeconds += (minutes * 60) + seconds;
                    count++;
                } catch (NumberFormatException e) {
                    
                    System.err.println("Could not parse time: " + timePart);
                }
            }
        }
        
        
        int avgSeconds = totalSeconds / count; //calculate the average time
        int avgMinutes = avgSeconds / 60;
        int avgRemainingSeconds = avgSeconds % 60;
        
        return String.format("%02d:%02d", avgMinutes, avgRemainingSeconds);
    }

    public static void main(String[] args) {
        launch();
    }
}