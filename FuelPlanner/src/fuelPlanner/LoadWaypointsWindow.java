package fuelPlanner;

import java.io.IOException;
import java.time.LocalDateTime;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class LoadWaypointsWindow {
	public static void display(Airplane airplane, LocalDateTime flightDate, 
							   Route route, int maxAlt) throws IOException {
		Stage window = new Stage();
		BorderPane root = new BorderPane();
		VBox mainLayout = new VBox();
		root.setCenter(mainLayout);
		Button nextButton = new Button("Next");
		BorderPane.setAlignment(nextButton, Pos.BOTTOM_LEFT);
		nextButton.setDisable(true);
		nextButton.setOnAction(e -> {
			window.close();
			ResultsWindow.display(route);
		});
		mainLayout.getChildren().add(nextButton);
		Scene scene = new Scene(root, 400, 400);
		window.setScene(scene);
		mainLayout.getChildren().add(new Label("Getting Weather Data"));
		window.show();
		route.getWx().genWeatherData(flightDate, route.getWaypoints(), maxAlt);
		mainLayout.getChildren().add(new Label("Got Weather Data"));
		mainLayout.getChildren().add(new Label("Calculating Route Data"));
		route.wpStatsLoop(airplane);
		mainLayout.getChildren().add(new Label("Done!"));
		nextButton.setDisable(false);
	}
}
