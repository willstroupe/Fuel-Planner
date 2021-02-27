package fuelPlanner;

import java.io.*;
import java.util.*;
import org.jdom2.*;
import org.jdom2.input.*;

public class Route {
	// array of route legs
	private RouteLeg[] waypoints;
	private int[][] wpAltData; // first number is altitude,
								// second number is restriction: 0-none 1-above 2-below 3-at
	private WeatherData wx;

	public Route(String routePath) {
		this.wx = new WeatherData();
		File routeXML = new File(routePath);
		wpBuilder(routeXML);
		distCrsFinder();

	}
	
	//after altitudes have been specified by user, run this
	public void setWpAltData(int[][] wpAltData) {
		this.wpAltData = wpAltData;
	}

	private void wpBuilder(File routeXML) {
		SAXBuilder builder = new SAXBuilder(); // read route from routeXML
		Document tempWP = null;
		try {
			tempWP = builder.build(routeXML);
		} catch (JDOMException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Element wpTable = tempWP.getRootElement().getChildren().get(1);
		List<Element> wpNodes = wpTable.getChildren();
		waypoints = new RouteLeg[wpNodes.size()];
		for (int i = 0; i < wpNodes.size(); i++) { // write lat+lon to this.waypoints
			Namespace nSpace = Namespace.getNamespace("http://www8.garmin.com/xmlschemas/FlightPlan/v1");
			double lat = Double.parseDouble(wpNodes.get(i).getChild("lat", nSpace).getText());
			double lon = Double.parseDouble(wpNodes.get(i).getChild("lon", nSpace).getText());
			String ident = wpNodes.get(i).getChild("identifier", nSpace).getText();
			String wpType = wpNodes.get(i).getChild("type", nSpace).getText();
			waypoints[i] = new RouteLeg(lat, lon, wx, ident, wpType);
		}
	}

	// use Haversine formula to find distance to next wp in nmi. In testing, gives
	// result to <1% accuracy
	private void distCrsFinder() {
		final double rEarth = 3440.0648; // radius of Earth in nmi
		for (int i = 0; i < waypoints.length - 1; i++) {
			double[] coords = { waypoints[i].getLat() * Math.PI / 180, // lat lon in radians
					waypoints[i].getLon() * Math.PI / 180, waypoints[i + 1].getLat() * Math.PI / 180,
					waypoints[i + 1].getLon() * Math.PI / 180 };
			double hav = Math.pow(Math.sin((coords[2] - coords[0]) / 2), 2)
					+ Math.cos(coords[0]) * Math.cos(coords[2]) * Math.pow(Math.sin((coords[3] - coords[1])) / 2, 2);
			double dist = 2 * rEarth * Math.asin(Math.sqrt(hav));
			waypoints[i].dist = dist;
			waypoints[i].cruiseDist = dist;
			double y = Math.sin(coords[3] - coords[1]) * Math.cos(coords[2]);
			double x = Math.cos(coords[0]) * Math.sin(coords[2])
					- Math.sin(coords[0]) * Math.cos(coords[2]) * Math.cos(coords[3] - coords[1]);
			double heading = Math.atan2(y, x);
			if (heading < 0)
				heading += 2 * Math.PI;
			waypoints[i].crs = heading;
		}
	}

	public RouteLeg[] getWaypoints() {
		return waypoints;
	}

	// this function will loop through waypoints using user altitude input, setting
	// altitudes which are achievable and calculating fuel burns+time for each leg
	public void wpStatsLoop(Airplane airplane) throws IOException {
		waypoints[0].setAlt(wpAltData[0][0]);
		for (int i = 0; i < waypoints.length - 1; i++) {
			this.wpStats(i, airplane);
		}
	}

	private void wpStats(int i, Airplane airplane) throws IOException  {
		if (waypoints[i].getAlt() == wpAltData[i+1][0]) {
			waypoints[i+1].setAlt(wpAltData[i+1][0]);
			waypoints[i].cruiseStats(airplane);
		}
		else if (waypoints[i].getAlt() < wpAltData[i+1][0]) {
			double climbDist = waypoints[i].climbStats(airplane, wpAltData[i+1][0])[2];
			if (climbDist <= waypoints[i].dist) {
				waypoints[i+1].setAlt(wpAltData[i+1][0]);
				waypoints[i].climbCruiseLegCalc(airplane, waypoints[i+1].getAlt());
			}
			else {
				if (wpAltData[i+1][1] == 0 || wpAltData[i+1][1] == 2) { // if  wp i+1 has unrestricted alt, change its alt
					int tempAlt = waypoints[i]
							.climbAltAfter(waypoints[i].dist, airplane);
					waypoints[i+1].setAlt(tempAlt);
					wpAltData[i+1][0] = tempAlt;
				}
				else { //if we need to be above wp i+1 alt, go backwards and change wp i's altitude
					if (wpAltData[i][1] == 2 || wpAltData[i][1] == 3) {
						throw new ArithmeticException("Route Altitudes are Invalid");
					}
					waypoints[i+1].setAlt(wpAltData[i+1][0]);
					int tempAlt = waypoints[i+1]
							.climbAltBefore(waypoints[i].dist, airplane);
					waypoints[i]
							.setAlt(tempAlt);
					wpAltData[i][0] = tempAlt;
					if (wpAltData[i][1] == 0) wpAltData[i][1] = 1;
					else wpAltData[i][1] = 3;
					wpStats(i-1, airplane); //i > 0 since wpAltData[0][1] = 3, so already caught
				}
				waypoints[i].climbLegCalc(airplane, waypoints[i+1].getAlt());
			}
		}
		else {
			double descDist = waypoints[i].descStats(airplane, wpAltData[i+1][0])[2];
			if (descDist <= waypoints[i].dist) {
				waypoints[i+1].setAlt(wpAltData[i+1][0]);
			}
			else {
				if (wpAltData[i+1][1] == 0 || wpAltData[i+1][1] == 1) { // if  wp i+1 has unrestricted alt, change its alt
					int tempAlt = waypoints[i]
							.descAlt(waypoints[i].dist, airplane);
					waypoints[i+1]
							.setAlt(tempAlt);
					wpAltData[i+1][0] = tempAlt;
				}
				else { //if we need to be below wp i+1 alt, instead go backwards and change wp i's altitude
					if (wpAltData[i][1] == 1 || wpAltData[i][1] == 3) {
						throw new ArithmeticException("Route Altitudes are Invalid");
					}
					waypoints[i+1].setAlt(wpAltData[i+1][0]);
					int tempAlt = waypoints[i+1]
							.descAlt(waypoints[i].dist, airplane);
					waypoints[i].setAlt(tempAlt);
					wpAltData[i][0] = tempAlt;
					if (wpAltData[i][1] == 0) wpAltData[i][1] = 2;
					else wpAltData[i][i] = 3;
					wpStats(i-1, airplane); //i > 0 since wpAltData[0][1] = 3, so already caught
				}
			}
			waypoints[i].descLegCalc(airplane, waypoints[i+1].getAlt());
		}
	}
	
	//get the total route distance
	public double getRouteDist() {
		double dist = 0;
		for (int i = 0; i < waypoints.length; i++) {
			dist += waypoints[i].dist;
		}
		return dist;
	}
	
	public WeatherData getWx() {
		return wx;
	}
}
