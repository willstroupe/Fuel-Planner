package fuelPlanner;
	
import java.io.File;
import java.io.FileNotFoundException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Arrays;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;


public class MainWindow extends Application {
	static private final String FPL_PATH = "src\\fuelPlanner\\Routes\\";
	static private final String PLANEID_PATH = "src\\fuelPlanner\\Aircraft\\";
	static ObservableList<String> fpls;
	static ObservableList<String> planeIDs;
	//time of flight in {year, month, day, hour} in UTC
	private int[] flightDateArray = new int[4];
	private String fpl;
	private String planeID;
	private LocalDateTime flightDate;
	@Override
	public void start(Stage primaryStage) {
		try {
			BorderPane root = new BorderPane();
			root.setPadding(new Insets(10, 10, 10, 10));
			Label fplSyntaxWarning = new Label("Please enter a valid date");
			fplSyntaxWarning.setTextFill(Color.web("#FF0000"));
			Label fplBeforeTimeWarning = new Label("Please enter a date after now");
			fplBeforeTimeWarning.setTextFill(Color.web("#FF0000"));
			Label fplAfterTimeWarning = new Label("Please enter a date before 16 days in the future");
			fplAfterTimeWarning.setTextFill(Color.web("#FF0000"));
				VBox mainLayout = new VBox();
					Label fplLabel = new Label("Select Flight Plan");
					ComboBox<String> fplSel = new ComboBox<String>(fpls);
					Label planeLabel = new Label("Select Plane");
					ComboBox<String> planeIDSel = new ComboBox<String>(planeIDs);
					Label dateLabel = new Label("Set date and hour in UTC");
					HBox dateBox = new HBox();
						TextField yearField = new TextField("yyyy");
						yearField.setPrefWidth(56);
						TextField monthField = new TextField("mm");
						monthField.setPrefWidth(36);
						TextField dayField = new TextField("dd");
						dayField.setPrefWidth(36);
						TextField hourField = new TextField("hh");
						hourField.setPrefWidth(36);
					dateBox.getChildren().addAll(yearField, monthField, 
												 dayField, hourField);
			Button nextButton = new Button("Next");
				
			//set date, fpl, and plane ID and check if date is invalid
			nextButton.setOnAction(new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					fpl = fplSel.getValue();
					planeID = planeIDSel.getValue();
					String[] flightDateString = {
							yearField.getText(),
							monthField.getText(),
							dayField.getText(),
							hourField.getText(),
					};
					//pass date input to the flightDate array
					for (int i = 0; i < 4; i++) {
						if (isPositiveInt(flightDateString[i])) {
							flightDateArray[i] = Integer.parseInt(flightDateString[i]);
						}
						else {
							root.setTop(fplSyntaxWarning);
							return;
						}
					}
					//make sure date is valid and within the right time range
					try {
						flightDate = LocalDateTime.of(flightDateArray[0], 
													  flightDateArray[1], 
													  flightDateArray[2], 
													  flightDateArray[3], 0);
						LocalDateTime timeNow = LocalDateTime.now();
						if (flightDate.isBefore(timeNow)) {
							root.setTop(fplBeforeTimeWarning);
							return;
						}
						if (flightDate.isAfter(timeNow.plusHours(384))) {
							root.setTop(fplAfterTimeWarning);
							return;
						}
					} catch (DateTimeException e) {
						root.setTop(fplSyntaxWarning);
						return;
					}
					primaryStage.close();
					try {
						WaypointDataWindow.display(FPL_PATH+fpl, 
												   PLANEID_PATH+planeID, 
												   flightDate);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
			});
			mainLayout.getChildren().addAll(fplLabel, fplSel, planeLabel, 
					                        planeIDSel, dateLabel, dateBox);
			root.setCenter(mainLayout);
			BorderPane.setAlignment(nextButton, Pos.BOTTOM_RIGHT);
			root.setBottom(nextButton);
			Scene scene = new Scene(root,400,400);
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		fpls = FXCollections.observableList(Arrays.asList(
				new File(FPL_PATH).list()));
		planeIDs = FXCollections.observableList(Arrays.asList(
				new File(PLANEID_PATH).list()));
		launch(args);
	}
	
	public static boolean isPositiveInt(String stringIn) {
		if (stringIn == null) return false;
		int strLength = stringIn.length();
		if (strLength == 0) return false;
		for (int i = 0; i < strLength; i++) {
			if (!Character.isDigit(stringIn.charAt(i))) return false;
		}
		return true;
	}
}
