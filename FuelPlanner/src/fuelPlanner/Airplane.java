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
	
	//given three waypoints at a set temperature and cruise airspeed, 
	//find dFuel/dAlt
	//dFuel/dAlt = dClimbFuel/dAlt + (cruiseDist)*dCruiseGPH/dAlt-dCruiseDist/dAlt*cruiseGPH
	/* public double getDFuel(RouteLeg wp0, RouteLeg wp1, RouteLeg wp2, double temp, double tas) {
		//get RPM for this tas
		double windCorrectionFactor = tas/(wp0.groundSpeed(tas));
		double cruiseDist = (wp0.cruiseDist + wp1.cruiseDist)*
							windCorrectionFactor; //correct for wind
		double dCruiseDist = 0;
		double dAltChangeFuel = 0;
		
		double[] dCruiseGPHTemp = getDGPHInCruise(wp1.getAlt(), temp, tas);
		double dCruiseGPH = dCruiseGPHTemp[0];
		int RPM = (int)dCruiseGPHTemp[1];
		double cruiseGPH = this.cruiseInterpolate(wp1.getAlt(), temp, RPM)[1];
		
		//find changes in cruise distance+climb fuel for wp0 -> wp1
		if (wp0.getAlt() <= wp1.getAlt()) {
			double[] dClimbTemp = getDClimbs(wp1.getAlt(), temp);
			dCruiseDist -= dClimbTemp[0];
			dAltChangeFuel += dClimbTemp[1];
		}
		else {
			double[] dDescTemp = getDDescs();
			dCruiseDist -= dDescTemp[0];
			dAltChangeFuel += dDescTemp[1];
		}

		//find changes in cruise distance+climb fuel for wp1 -> wp2
		if (wp1.getAlt() < wp2.getAlt()) {
			double[] dClimbTemp = getDClimbs(wp2.getAlt(), temp);
			dCruiseDist -= dClimbTemp[0];
			dAltChangeFuel += dClimbTemp[1];
		}
		else if (wp1.getAlt() > wp2.getAlt()) {
			double[] dDescTemp = getDDescs();
			dCruiseDist -= dDescTemp[0];
			dAltChangeFuel += dDescTemp[1];
		}
		
		dCruiseDist *= windCorrectionFactor;
		return dAltChangeFuel + cruiseDist*dCruiseGPH + dCruiseDist*cruiseGPH;	
	}

	//return {dGPH, cruise RPM for this tas}
	public double[] getDGPHInCruise(int alt, double temp, double tas) {
		int[] bracketAlts = null;
		int aLow = 0;
		int aHigh = 0;
		double aProp = 0;
		//find bracketing cruise alts and next closest alt to wp1Alt
		for (int i = 0; i < cruiseStats[1].length; i++) {
			if (cruiseStats[1][i] >= alt) {
				if (i == 0) {
					bracketAlts = new int[]{i, i+1};
					aLow = 0;
					aHigh = 0;
					aProp = 0;
				}
				else if (i == cruiseStats[1].length - 1) {
					aLow = i-1;
					aHigh = i;
					aProp = (alt - cruiseStats[1][i-1]) / (cruiseStats[1][i] - cruiseStats[1][i-1]);
					if (alt >= (cruiseStats[1][i] + cruiseStats[1][i-1])/2) {
						bracketAlts = new int[] {i-1, i};
						
					}
					else {
						bracketAlts = new int[] {i-2, i-1, i};
					}
				}
				else {
					aLow = i-1;
					aHigh = i;
					aProp = (alt - cruiseStats[1][i-1]) / (cruiseStats[1][i] - cruiseStats[1][i-1]);
					if (alt >= (cruiseStats[1][i] + cruiseStats[1][i-1])/2) {
						bracketAlts = new int[] {i-1, i, i+1};
					}
					else {
						bracketAlts = new int[] {i-2, i-1, i};
					}
				}
				break;
			}
			if (i == cruiseStats[1].length - 1) {
				bracketAlts = new int[] {i-1, i};
			}
		}
		
		//find bracketing temp values at this alt
		int tLow;
		int tHigh;
		double tProp;
		int count = 0;
		while (true) {
			//if we've gone over the highest temp value, backtrack to highest
			if (count > cruiseStats[2].length-1) {
				tLow = count-2;
				tHigh = count-2;
				tProp = 1;
			}
			if (temp > cruiseStats[2][count]) {
				count++;
			}
			else if (temp < cruiseStats[2][count]){
				tLow = count-1;
				tHigh = count;
				tProp = (temp-cruiseStats[2][count-1])/
				(cruiseStats[2][count]-cruiseStats[2][count-1]); //position of temp between bounding values
				break;
			}
			else {
				tLow = count;
				tHigh = count;
				tProp = 0;
				break;
			}
		}

		//find bracketing RPM values at this tas
		int rLow = cruiseStats[0][cruiseStats[0].length-1];
		int rHigh = cruiseStats[0][cruiseStats[0].length-1];
		double rProp = 0;
		double tasAtLowRPM = 0;
		for (int i = 0; i < cruiseStats[0].length; i++) {
			//at cruiseStats[0][i] find TAS at alt+temp
			double tasAAlt = tProp*(cruiseSpeed[i][aHigh][tHigh]) + (1-tProp)*(cruiseSpeed[i][aHigh][tLow]);
			double tasBAlt = tProp*(cruiseSpeed[i][aLow][tHigh]) + (1-tProp)*(cruiseSpeed[i][aLow][tLow]);
			double tasAtRPM = aProp*(tasAAlt) + (1-aProp)*(tasBAlt);
			if (tasAtRPM < tas) {
				tasAtLowRPM = tasAtRPM;
			}
			else {
				//check edge cases
				if (i == 0) {
					rLow = i;
					rHigh = i;
					rProp = 0;
				}
				else {
					rLow = i - 1;
					rHigh = i;
					//find rpm at which tasAtRPM == tas
					rProp = (tas - tasAtLowRPM)/(tasAtRPM - tasAtLowRPM);
				}
				break;
			}
		}
		double RPM = rProp*cruiseStats[0][rHigh]+(1-rProp)*cruiseStats[0][rLow];
		int[] b = {bracketAlts[0], bracketAlts[1], rLow, rHigh, tLow, tHigh};
		double[] p = {0, rProp, tProp};
		double dGPHCruise = 0;
		//from bracketing alt, temp, and rpm find dTas/dAlt, dRPM/dTas, and dGPH/dRPM to get dGPH/dAlt
		if (bracketAlts.length == 2) {
			dGPHCruise = findDGph(b, p);
		}
		else {
			int aMidLow = (cruiseStats[1][bracketAlts[1]] + cruiseStats[1][bracketAlts[0]])/2;
			int aMidHigh = (cruiseStats[1][bracketAlts[2]] + cruiseStats[1][bracketAlts[1]])/2;
			double aMidProp = (alt - aMidLow)/(aMidHigh - aMidLow);
			double dGPHCruiseLow = findDGph(b, p);
			b[0] = bracketAlts[1];
			b[1] = bracketAlts[2];
			double dGPHCruiseHigh = findDGph(b, p);
			dGPHCruise = aMidProp*dGPHCruiseHigh + (1-aMidProp)*dGPHCruiseLow;
		}
		double[] rVal = new double[] {dGPHCruise, RPM};
		return rVal;
	}
	
	//return {dDist/dAlt, dFuel/dAlt} in a climb
	//at specified altitude, temperature
	private double[] getDClimbs(int alt, double temp) {
		double[] rVal = new double[2];
		int[] brackets = null;
		//find bracketing alts of alt
		for(int i = 0; i < climbStats.length; i++) {
			if (climbStats[i][0] >= alt) {
				if (i == 0) {
					brackets = new int[]{i, i+1};
				}
				else if (i == climbStats.length - 1) {
					if (alt >= (climbStats[i][0] + climbStats[i-1][0])/2) {
						brackets = new int[] {i-1, i};
						
					}
					else {
						brackets = new int[] {i-2, i-1, i};
					}
				}
				else {
					if (alt >= (climbStats[i][0] + climbStats[i-1][0])/2) {
						brackets = new int[] {i-1, i, i+1};
					}
					else {
						brackets = new int[] {i-2, i-1, i};
					}
				}
				break;
			}
			if (i == cruiseStats[1].length - 1) {
				brackets = new int[] {i-1, i};
			}
		}
		double dividend = climbStats[brackets[1]][0]-climbStats[brackets[0]][0];
		rVal[0] = (climbStats[brackets[1]][3]-climbStats[brackets[0]][3])/dividend;
		rVal[1] = (climbStats[brackets[1]][2]-climbStats[brackets[0]][2])/dividend;
			
		if (brackets.length == 3) {
			int aMidLow = (int)(climbStats[brackets[1]][0]+climbStats[brackets[0]][0])/2;
			int aMidHigh = (int)(climbStats[brackets[2]][0]+climbStats[brackets[1]][0])/2;
			double aMidProp = (alt - aMidLow)/(aMidHigh - aMidLow);
			
			double highDividend = climbStats[brackets[2]][0]-climbStats[brackets[1]][0];
			rVal[0] = aMidProp*(climbStats[brackets[2]][3]-climbStats[brackets[1]][3])/highDividend+
					(1-aMidProp)*rVal[0];
			rVal[1] = aMidProp*(climbStats[brackets[2]][2]-climbStats[brackets[1]][2])/highDividend+
					(1-aMidProp)*rVal[1];
		}
		
		//adjust for temperature above/below std
		rVal[0] *= (1+temp/100);
		rVal[1] *= (1+temp/100);
		return rVal;
	}

	//return {dDist/dAlt, dFuel/dAlt} in a descent
	private double[] getDDescs() {
		double dDist = this.descStats[0]/this.descStats[1]/60; //convert from minutes to hours
		double dFuel = this.descStats[2]/this.descStats[1]/60; //convert from minutes to hours
		return new double[]{dDist, dFuel};
	}
	
	private double findDGph(int[] b, double[] p) {

		//set dTas: find tas at alt above, find tas at alt below, divide by alt difference
		double tasAAltATemp = p[1]*(cruiseSpeed[b[3]][b[1]][b[5]])+(1-p[1])*(cruiseSpeed[b[2]][b[1]][b[5]]);
		double tasAAltBTemp = p[1]*(cruiseSpeed[b[3]][b[1]][b[4]])+(1-p[1])*(cruiseSpeed[b[2]][b[1]][b[4]]);
		double tasBAltATemp = p[1]*(cruiseSpeed[b[3]][b[0]][b[5]])+(1-p[1])*(cruiseSpeed[b[2]][b[0]][b[5]]);
		double tasBAltBTemp = p[1]*(cruiseSpeed[b[3]][b[0]][b[4]])+(1-p[1])*(cruiseSpeed[b[2]][b[0]][b[4]]);
		double tasAAlt = p[2]*tasAAltATemp + (1-p[2])*tasAAltBTemp;
		double tasBAlt = p[2]*tasBAltATemp + (1-p[2])*tasBAltBTemp;
		double dTas = (tasAAlt-tasBAlt)/(cruiseStats[1][b[1]] - cruiseStats[1][b[0]]);
		
		//set dRPM: find tas at RPM above, and at RPM below, divide RPM by difference
		double tasARPMATemp = p[0]*(cruiseSpeed[b[3]][b[1]][b[5]]) + (1-p[0])*(cruiseSpeed[b[3]][b[0]][b[5]]);
		double tasARPMBTemp = p[0]*(cruiseSpeed[b[3]][b[1]][b[4]]) + (1-p[0])*(cruiseSpeed[b[3]][b[0]][b[4]]);
		double tasBRPMATemp = p[0]*(cruiseSpeed[b[2]][b[1]][b[5]]) + (1-p[0])*(cruiseSpeed[b[2]][b[0]][b[5]]);
		double tasBRPMBTemp = p[0]*(cruiseSpeed[b[2]][b[1]][b[4]]) + (1-p[0])*(cruiseSpeed[b[2]][b[0]][b[4]]);
		double tasARPM = p[2]*tasARPMATemp + (1-p[2])*tasARPMBTemp;
		double tasBRPM = p[2]*tasBRPMATemp + (1-p[2])*tasBRPMBTemp;
		double dRPM = (cruiseStats[0][b[3]] - cruiseStats[0][b[2]])/(tasARPM-tasBRPM);
		
		//set dGPH: find GPH at RPM above, and at RPM below, divide by RPM difference
		double gphARPMATemp = p[0]*(cruiseFuel[b[3]][b[1]][b[5]]) + (1-p[0])*(cruiseFuel[b[3]][b[0]][b[5]]);
		double gphARPMBTemp = p[0]*(cruiseFuel[b[3]][b[1]][b[4]]) + (1-p[0])*(cruiseFuel[b[3]][b[0]][b[4]]);
		double gphBRPMATemp = p[0]*(cruiseFuel[b[2]][b[1]][b[5]]) + (1-p[0])*(cruiseFuel[b[2]][b[0]][b[5]]);
		double gphBRPMBTemp = p[0]*(cruiseFuel[b[2]][b[1]][b[4]]) + (1-p[0])*(cruiseFuel[b[2]][b[0]][b[4]]);
		double gphARPM = p[2]*gphARPMATemp + (1-p[2])*gphARPMBTemp;
		double gphBRPM = p[2]*gphBRPMATemp + (1-p[2])*gphBRPMBTemp;
		double dGPH = (gphARPM-gphBRPM)/(cruiseStats[0][b[3]] - cruiseStats[0][b[2]]);
		return dTas*dRPM*dGPH;
	} */
	
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
		int i;
		for (i = 1; i < climbStats.length; i++) {
			if (climbStats[i][0] > alt) break;
		}
		double aProp = (alt-climbStats[i-1][0])/(climbStats[i][0]-climbStats[i-1][0]);
		for (int j = 0; j < 3; j++) {
			rVal[j] = aProp*climbStats[i][j+1]+(1-aProp)*climbStats[i-1][j+1];
		}
		return rVal;
	}
	
	//returns altitude reached if climbing the input distance from sea level
	public int climbDistInterpolate(double dist) {
		int i;
		for(i = 1; i < climbStats.length; i++) {
			if (climbStats[i][3] > dist) break;
		}
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
			}
			if (temp > cruiseStats[2][count]) {
				count++;
			}
			else if (temp < cruiseStats[2][count]){
				tLow = count-1;
				tProp = (temp-cruiseStats[2][count-1])/
				(cruiseStats[2][count]-cruiseStats[2][count-1]); //position of temp between bounding values
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
