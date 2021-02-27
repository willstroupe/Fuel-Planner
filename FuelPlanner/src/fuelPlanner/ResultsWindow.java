package fuelPlanner;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ResultsWindow {
	public static void display(Route route) {
		RouteLeg[] waypoints = route.getWaypoints();
		double totalFuel = 0;
		double totalTime = 0;
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
				Label crossAlt = new Label("Crossing Altitude: " + waypoints[i].getAlt());
				Label fuelBurn = new Label("Fuel Used (Gal): " + waypoints[i].getLegFuel());
				totalFuel += waypoints[i].getLegFuel();
				Label time = new Label("Leg Time (Hours): " + waypoints[i].getLegTime());
				totalTime += waypoints[i].getLegTime();
			tempVBox.getChildren().addAll(wpLabel, wpIdentLabel, crossAlt, fuelBurn, time);
			wpBox.getChildren().add(tempVBox);
		}
		VBox tmpVBox = new VBox();
		tmpVBox.getChildren().addAll(new Label("Total fuel (Gal): "+totalFuel),
									 new Label("Total time (Hours): "+totalTime));
		wpBox.getChildren().add(tmpVBox);
		root.setCenter(wpBox);
		Scene scene = new Scene(root, 1200, 400);
		window.setScene(scene);
		window.show();
	}
}
