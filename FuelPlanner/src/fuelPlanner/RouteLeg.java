package fuelPlanner;

import java.io.IOException;

public class RouteLeg {
	WeatherData wx;
	double[] wxData;
	
	private double lat;
	private double lon;
	double dist = 0;
	double cruiseDist = 0;
	private int alt;
	double crs; //In radians;
	double crabAngle;
	double tas;
	private int RPM = 2400;
	
	private double legTime;
	private double legFuel;
	
	private String ident;
	private String wpType;
	
	public RouteLeg(double lat, double lon, WeatherData wx, String ident, String wpType) {
		this.lat = lat;
		this.lon = lon;
		this.wx = wx;
		if (wpType.equals("USER WAYPOINT")) {
			this.ident = "";
			this.wpType = "Coordinates";
		}
		else {
			this.ident = ident;
			if (wpType.equals("AIRPORT")) this.wpType = "AD";
			else this.wpType = wpType;
		}
	}
	
	public void setRPM(int RPM) {
		this.RPM = RPM;
	}
	
	public void setWxData() {
		try {
			wxData = wx.getWPWeather(lat, lon, alt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getIdent() {
		return ident;
	}
	
	public String getWpType() {
		return wpType;
	}
	
	public double getLegTime() {
		return legTime;
	}
	
	public double getLegFuel() {
		return legFuel;
	}
	
	public double getLat() {
		return lat;
	}
	
	public double getLon() {
		return lon;
	}
	
	public void setAlt(int alt) {
		this.alt = alt;
		this.setWxData();
	}
	
	public int getAlt() {
		return this.alt;
	}
	
	//sets legTime, legFuel, time in hours
	public void cruiseStats(Airplane airplane) throws IOException {
		double[] planeInfo = airplane.cruiseInterpolate(alt, wxData[3], RPM);
		double groundSpeed = this.groundSpeed(planeInfo[0]);
		double time = cruiseDist/groundSpeed;
		legTime = time;
		legFuel = time*planeInfo[1];
	}
	
	//returns groundspeed for use in functions
	public double groundSpeed(double speed) {
		double windDir = Math.PI/2-Math.atan2(wxData[1], wxData[2]);
		double windSpd = Math.sqrt(wxData[1]*wxData[1]+wxData[2]*wxData[2]);
		double windPar = windSpd*Math.cos(windDir-crs);
		double windPerp = windSpd*Math.sin(windDir-crs); //positive is to the right, negative to the left
		//find parallel TAS of plane once crabbed into wind
		crabAngle = Math.PI/2-Math.acos(windPerp/speed);
		crabAngle *= -1; //reverse crab angle so it cancels out the perp. component
		//add parallel TAS and parallel wind to find GS
		double groundSpeed = speed*Math.cos(crabAngle)+windPar;
		return groundSpeed;
	}
	
	//returns [time][fuel burn][distance] to climb to endAlt, time in hours
	public double[] climbStats(Airplane airplane, int endAlt) throws IOException {
		double[] stVals = airplane.climbAltInterpolate(alt);
		double[] endVals = airplane.climbAltInterpolate(endAlt);
		double[] clmbVals = new double[3];
		for(int i = 0; i < 3; i++) {
			clmbVals[i] = endVals[i]-stVals[i];
			clmbVals[i] *= 1+.01*(wxData[0]-(15-alt/500));
		}
		double gSpeed = this.groundSpeed(airplane.getClimbTas());
		clmbVals[2] *= gSpeed/airplane.getClimbTas(); //correct distance covered in climb for increased or decreased groundspeed in climb
		clmbVals[0] /= 60; //convert minutes to hours for time
		return clmbVals;
	}
	
	//run when a leg has both a climbing and cruising section -- determined in Route
	public void climbCruiseLegCalc(Airplane airplane, int endAlt) throws IOException {
		double[] clmbVals = climbStats(airplane, endAlt);
		cruiseDist = dist - clmbVals[2];
		alt = endAlt;
		this.cruiseStats(airplane);
		legTime += clmbVals[0];
		legFuel += clmbVals[1];
	}
	
	//run when a leg is only a climbing section -- determined in Route
	public void climbLegCalc(Airplane airplane, int endAlt) throws IOException {
		double[] clmbVals = climbStats(airplane, endAlt);
		legTime = clmbVals[0];
		legFuel = clmbVals[1];
		double distDiff = dist - clmbVals[2];
		cruiseDist = 0;
		System.out.println("The difference between calculated climb distances is "+distDiff);
	}
	public void descLegCalc(Airplane airplane, int endAlt) throws IOException {
		double descVals[] = descStats(airplane, endAlt);
		cruiseDist = dist - descVals[2];
		this.cruiseStats(airplane);
		legTime += descVals[0];
		legFuel += descVals[1];
		
	}
	
	//returns [time][fuel burn][distance] to descend to endAlt
	public double[] descStats(Airplane airplane, int endAlt) {
		double[] descStats = airplane.getDescStats();
		double[] returnVals = new double[3];
		returnVals[0] = ((alt-endAlt)/descStats[1])/60;
		returnVals[1] = returnVals[0]*descStats[2];
		returnVals[2] = returnVals[0]*descStats[0];
		return returnVals;
	}
	
	//find how high the plane can climb from wp alt in given dist
	public int climbAltAfter(double dist, Airplane airplane) {
		double stDist = airplane.climbAltInterpolate(alt)[2];
		dist *= airplane.getClimbTas()/groundSpeed(airplane.getClimbTas()); //since the faster your GS, the shorter your effective distance
		dist /= 1+.01*(wxData[0]-(15-alt/500)); //correct for nonstandard temp 
		int endAlt = airplane.climbDistInterpolate(dist+stDist);
		System.out.println(endAlt);
		alt = endAlt;
		return endAlt;
	}
	
	//find the lowest alt the plane can climb from to wp alt in given dist
	public int climbAltBefore(double dist, Airplane airplane) {
		double[] endVals = airplane.climbAltInterpolate(alt);
		dist *= airplane.getClimbTas()/groundSpeed(airplane.getClimbTas()); //since the faster your GS, the shorter your effective distance
		dist /= 1+.01*(wxData[0]-(15-alt/500)); //correct for nonstandard temp
		int stAlt = Math.max(airplane.climbDistInterpolate(endVals[2]-dist), 0);
		return stAlt + (int)((alt-stAlt)*.1); //reduce the change in altitude by 10% for a safety buffer
	}
	
	//find how far the plane can descend in given dist
	public int descAlt(double dist, Airplane airplane) {
		double[] descStats = airplane.getDescStats();
		double gSpeed = this.groundSpeed(descStats[0]);
		double descTime = dist/gSpeed;
		int alt = (int)(descStats[1]*descTime*60); //mult. by 60 to convert to minutes and FPM
		return alt;
	}
	
	//returns the altitude + RPM where fuel burn is optimized for given tas+temp
}
