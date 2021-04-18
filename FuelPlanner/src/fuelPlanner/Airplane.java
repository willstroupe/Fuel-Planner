package fuelPlanner;

import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;

public class Airplane {
	String folderPath = "src\\fuelPlanner\\Aircraft\\";
	private final double climbTas;
	//aircraft {altitude, time to altitude, fuel to altitude, dist to altitude} in climb
	private double[][] climbStats;
	// {rpm[], alt[], temp[]} divisions of aircraft data
	private int[][] cruiseStats = new int[3][];
	// aircraft cruise speed in TAS at [rpm][alt][temp]
	private double[][][] cruiseSpeed;
	// aircraft fuel flow in GPH at [rpm][alt][temp]
	private double[][][] cruiseFuel;
	// aircraft {descent TAS, descent FPM, descent GPH}
	private double[] descStats;
	
	public Airplane(String planePathString) throws FileNotFoundException {
		this.climbTas = 80; //set for c172, TODO change depending on aircraft
		this.descStats = new double[]{110, 600, 6};
		StringBuilder planePath = new StringBuilder(planePathString);
		planePath.append("\\");
		File cruiseFile = new File(planePath.toString()+"Cruise.txt");
		Scanner scanner = new Scanner(cruiseFile);
		loadAxes(scanner);
		int[] sizes = {cruiseStats[0].length, cruiseStats[1].length, cruiseStats[2].length};
		cruiseSpeed = new double[sizes[0]][sizes[1]][sizes[2]];
		cruiseFuel = new double[sizes[0]][sizes[1]][sizes[2]];
		loadCruise(scanner, sizes);
		File climbFile = new File(planePath.toString()+"Climb.txt");
		Scanner climbScanner = new Scanner(climbFile);
		loadClimb(climbScanner);
	}

	//return {low, high} values of RPM, Alt, and Temp data for the plane
	public int[] getRPMRange() {
		int[] rVal = {cruiseStats[0][0], cruiseStats[0][cruiseStats[0].length-1]};
		return rVal;
	}
	public int[] getAltRange() {
		int[] rVal = {cruiseStats[1][0], cruiseStats[1][cruiseStats[1].length-1]};
		return rVal;
	}
	public int[] getTempRange() {
		int[] rVal = {cruiseStats[2][0], cruiseStats[2][cruiseStats[2].length-1]};
		return rVal;
	}
	
	// get first three lines of data and put them into cruiseStats
	void loadAxes(Scanner scanner) {
		for (int i = 0; i < 3; i++) {
			String input = scanner.nextLine();
			String[] nums = input.split(" ");
			cruiseStats[i] = new int[nums.length];
			for (int j = 0; j < nums.length; j++) {
				cruiseStats[i][j] = Integer.parseInt(nums[j]);
			}
		}
		scanner.nextLine();
	}
	
	void loadCruise(Scanner scanner, int[] sizes) {
		for (int i = 0; i < sizes[0]; i++) { //iterate over rpm
			for (int j = 0; j < sizes[1]; j++) { //iterate over alt
				String[] tempLine = scanner.nextLine().split(" ");				
				for (int k = 0; k < sizes[2]; k++) { //iterate over temp
					String[] entry = tempLine[k].split(",");
					cruiseFuel[i][j][k] = Double.parseDouble(entry[1]);
					cruiseSpeed[i][j][k] = Double.parseDouble(entry[0]);
				}
			}
			scanner.nextLine();
		}
	}
	
	void loadClimb(Scanner scanner) {
		int lines = Integer.parseInt(scanner.nextLine());
		climbStats = new double[lines][4];
		for(int i = 0; i < lines; i++) {
			String[] tempLine = scanner.nextLine().split(" ");
			for(int j = 0; j < 4; j++) {
				climbStats[i][j] = Double.parseDouble(tempLine[j]);
			}
		}
	}
	
	//returns {time, fuel, dist} to get from SL to specified alt at std temp
	public double[] climbAltInterpolate(int alt) {
		double[] rVal = new double[3];
		int i = 1;
		while (climbStats[i][0] <= alt && i < climbStats.length - 1) i++;
		double aProp = (alt-climbStats[i-1][0])/(climbStats[i][0]-climbStats[i-1][0]);
		for (int j = 0; j < 3; j++) {
			rVal[j] = aProp*climbStats[i][j+1]+(1-aProp)*climbStats[i-1][j+1];
		}
		return rVal;
	}
	
	//returns altitude reached if climbing the input distance from sea level
	public int climbDistInterpolate(double dist) {
		int i = 1;
		while (climbStats[i][3] <= dist && i < climbStats.length - 1) i++;
		System.out.println(climbStats.length);
		System.out.println(i);
		double dProp = (dist-climbStats[i-1][3])/(climbStats[i][3]-climbStats[i-1][3]);
		int rVal = (int)(dProp*climbStats[i][0]+(1-dProp)*climbStats[i-1][0]);
		return rVal;
	}
	
	//returns {speed(tas), fuel flow(gph)} given alt, temp, and RPM
	public double[] cruiseInterpolate(int alt, double temp, int RPM) {
		//finding indices of high and low bounding values and input position between them in cruiseStats
		int aLow, tLow, rLow;
		double  aProp, tProp, rProp;
		int count = 0;
		while (true) { //get RPM values
			//if we've gone over the highest RPM value, backtrack to highest
			if (count > cruiseStats[0].length-1) {
				rLow = count-2;
				rProp = 1;
			}
			if (RPM > cruiseStats[0][count]) {
				count++;
			}
			else if (RPM < cruiseStats[0][count]){
				if (count == 0) {
					rLow = count;
					rProp = 0;
					break;
				}
				else {
					rLow = count-1;
					rProp = (alt-cruiseStats[0][count-1])/   //position of alt between bounding values
					(cruiseStats[0][count]-cruiseStats[0][count-1]);
					break;
				}
			}
			else {
				rLow = count;
				rProp = 0;
				break;
			}
		}
		count = 0;
		while (true) { //get alt values
			//if we've gone over the highest alt value, backtrack to highest
			if (count > cruiseStats[1].length-1) {
				aLow = count-2;
				aProp = 1;
			}
			if (alt > cruiseStats[1][count]) {
				count++;
			}
			else if (alt < cruiseStats[1][count]){
				if (count == 0) {
					aLow = count;
					aProp = 0;
					break;
				}
				else {
					aLow = count-1;
					aProp = (alt-cruiseStats[1][count-1])/   //position of alt between bounding values
					(cruiseStats[1][count]-cruiseStats[1][count-1]);
					break;
				}
			}
			else {
				aLow = count;
				aProp = 0;
				break;
			}
		}
		count = 0;
		while (true) { //get temp values
			//if we've gone over the highest temp value, backtrack to highest
			if (count > cruiseStats[2].length-1) {
				tLow = count-2;
				tProp = 1;
				break;
			}
			if (temp > cruiseStats[2][count]) {
				count++;
			}
			else if (temp < cruiseStats[2][count]){
				tLow = count-1;
				//position of temp between bounding values
				tProp = (temp-cruiseStats[2][count-1])/
				(cruiseStats[2][count]-cruiseStats[2][count-1]);
				break;
			}
			else {
				tLow = count;
				tProp = 0;
				break;
			}
		}
		//finding the additional speed/fuel above aLow,tLow and adding to speed/fuel at aLow,tLow, rLow
		double delSpeed = aProp*(cruiseSpeed[rLow][aLow+1][tLow]-cruiseSpeed[rLow][aLow][tLow])+
		tProp*(cruiseSpeed[rLow][aLow][tLow+1]-cruiseSpeed[rLow][aLow][tLow])+
		rProp*(cruiseSpeed[rLow+1][aLow][tLow]-cruiseSpeed[rLow][aLow][tLow]);
		double delFuel = aProp*(cruiseFuel[rLow][aLow+1][tLow]-cruiseFuel[rLow][aLow][tLow])+
		tProp*(cruiseFuel[rLow][aLow][tLow+1]-cruiseFuel[rLow][aLow][tLow])+
		rProp*(cruiseFuel[rLow+1][aLow][tLow]-cruiseFuel[rLow][aLow][tLow]);
		double[] rVal = {cruiseSpeed[rLow][aLow][tLow]+delSpeed, cruiseFuel[rLow][aLow][tLow]+delFuel};
		return rVal;
	}
	
	public double getClimbTas() {
		return climbTas;
	}
	
	public double[] getDescStats() {
		return descStats;
	}

}
