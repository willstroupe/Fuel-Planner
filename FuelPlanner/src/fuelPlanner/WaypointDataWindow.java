package fuelPlanner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class WaypointDataWindow {
	public static void display(String routePath, String planePath, LocalDateTime flightDate) throws FileNotFoundException {
		Route fltRoute = new Route(routePath);
		Airplane airplane = new Airplane(planePath);
		RouteLeg[] waypoints = fltRoute.getWaypoints();
		
		Stage window = new Stage();
		BorderPane root = new BorderPane();
		HBox wpBox = new HBox();

		//for each waypoint set up a hbox member to set its altitude and RPM
		for (int i = 0; i < waypoints.length; i++) {
			VBox tempVBox = new VBox();
				Label wpLabel = new Label("Waypoint "+i);
				StringBuilder wpIdentString = new StringBuilder(waypoints[i].getIdent());
				wpIdentString.append(" (");
				wpIdentString.append(waypoints[i].getWpType());
				wpIdentString.append(")");
				Label wpIdentLabel = new Label(wpIdentString.toString());
				TextField altInput = new TextField("alt");
				TextField rpmInput = new TextField("RPM");
			tempVBox.getChildren().addAll(wpLabel, wpIdentLabel, altInput, rpmInput);
			if (i != 0 && i != waypoints.length-1) {
				Label altRestrLabel = new Label("Altitude Restriction");
				ComboBox<String> altRestrBox = new ComboBox<String>();
				altRestrBox.getItems().add("None");
				altRestrBox.getItems().add("Above");
				altRestrBox.getItems().add("Below");
				altRestrBox.getItems().add("At");
				altRestrBox.getSelectionModel().select(0);
				tempVBox.getChildren().addAll(altRestrLabel, altRestrBox);
			}
			wpBox.getChildren().add(tempVBox);
		}
		Button nextButton = new Button("Next");
		nextButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				int[][] wpAltData = new int[waypoints.length][2];
				int maxAlt = 0;
				for (int i = 0; i < waypoints.length; i++) {
					VBox dataBox = (VBox)wpBox.getChildren().get(i);
					String wpAltStr = ((TextField)dataBox.getChildren().get(2)).getText();
					String wpRPMStr = ((TextField)dataBox.getChildren().get(3)).getText();
					int wpAltRestr = 3;
					if (i != 0 && i != waypoints.length-1) {
						String wpAltRestrStr = ((ComboBox<String>)dataBox.getChildren().get(5)).getValue();
						System.out.println(wpAltRestrStr);
						switch (wpAltRestrStr) {
							case "None":
								wpAltRestr = 0;
								break;
							case "Above":
								wpAltRestr = 1;
								break;
							case "Below":
								wpAltRestr = 2;
								break;
							case "At":
								wpAltRestr = 3;
								break;
						}
					};
					wpAltData[i][1] = wpAltRestr;
					if (MainWindow.isPositiveInt(wpAltStr) && 
						MainWindow.isPositiveInt(wpRPMStr)) {
						int wpAlt = Integer.parseInt(wpAltStr);
						int wpRPM = Integer.parseInt(wpRPMStr);
						wpAltData[i][0] = wpAlt;
						waypoints[i].setRPM(wpRPM);
						if (wpAlt > maxAlt) maxAlt = wpAlt;
					}
					else {
						Label warningLabel = new Label(
								"Please enter a non-negative integer for altitude and RPM");
						warningLabel.setTextFill(Color.web("#FF0000"));
						root.setTop(warningLabel);
						return;
					}
				}
				fltRoute.setWpAltData(wpAltData);
				window.close();
				for(int j = 0; j < wpAltData.length; j++) {
					System.out.println(wpAltData[j][1]);
				}
				try {
					LoadWaypointsWindow.display(airplane, flightDate, fltRoute, maxAlt);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		root.setCenter(wpBox);
		BorderPane.setAlignment(nextButton, Pos.BOTTOM_RIGHT);
		root.setBottom(nextButton);
		Scene scene = new Scene(root, 1200, 400);
		window.setScene(scene);
		window.show();
	}
}