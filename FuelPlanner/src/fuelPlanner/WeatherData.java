package fuelPlanner;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class WeatherData {
	private String grib2Path;
	private final double feetPerMeter = 3.28084;
	private final double KtoC = -273.15;
	private final double MStoKt = 0.514444;
	private final String wgribPath = "src\\fuelPlanner\\grib\\wgrib2\\wgrib2.exe";
	private final String dataPath = "src\\fuelPlanner\\grib\\gribData\\";
	private final String getGribPath = "src\\fuelPlanner\\grib\\getGrib\\";
	private final String[] finalVars = { ":TMP:", ":UGRD:", ":VGRD:", ":HGT:" };
	private String[] finalHGTs;

	//after we have flight time and route, generate weather data
	public void genWeatherData(LocalDateTime flightDate, RouteLeg[] waypoints, int maxAlt) throws IOException {
			this.getFilePath(flightDate);
			this.getPresAlts(waypoints, maxAlt);
			this.getWeatherGrib();
	}

	// given a flight time, find the .grib2 file which provides data closest to that
	// time, in {year, month, day, hour}
	private void getFilePath(LocalDateTime flightDate) {
		LocalDateTime dtTm = LocalDateTime.ofInstant(Instant.now(), 
				ZoneOffset.UTC).minusHours(4);
		// round our hours down to the nearest multiple of six
		dtTm = dtTm.minusHours(dtTm.getHour() % 6);
		long fVal = dtTm.until(flightDate, ChronoUnit.HOURS);
		// round fVal to the nearest multiple of 3
		fVal = 3 * Math.round(((double) fVal) / 3);
		String fStr = String.format("%03d", fVal);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd/HH");
		String dtStr = dtTm.format(formatter);
		String hrStr = String.format("%02d", dtTm.getHour());
		StringBuilder path = new StringBuilder(
				"https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/gfs.");
		path.append(dtStr);
		path.append("/atmos/gfs.t");
		path.append(hrStr);
		path.append("z.pgrb2.1p00.f");
		path.append(fStr);
		this.grib2Path = path.toString();
		System.out.println(grib2Path);
	}

	// take file from getFilePath, download HGT data, and find lowest pres alt+its
	// line in grib2 above cruising alt
	private void getPresAlts(RouteLeg[] waypoints, int maxAlt) throws IOException {
		System.out.println("Getting Height Data");
		List<int[]> latLon = new ArrayList<int[]>(); // list of closest lat/lon to route

		// find all waypoint lat/lon and put them in latLon
		for (int i = 0; i < waypoints.length; i++) {
			boolean noRepeat = true;
			int[] wpLatLon = { (int) Math.round(waypoints[i].getLat()),
					(int) Math.round(waypoints[i].getLon()) };
			for (int j = 0; j < latLon.size(); j++) {
				if (wpLatLon == latLon.get(j))
					noRepeat = false;
			}
			if (noRepeat)
				latLon.add(wpLatLon);
		}

		// use get_inv.exe to download .idx file and write to invOutput.txt for parsing
		getInv();

		// delete all lines in invOutput.txt which are not between 111 and 420 and are
		// not the HGT field
		Scanner invScanner = new Scanner(new File(dataPath + "inv.txt"));
		String thisLine;
		int line = 1;
		List<String> hgtInvS = new ArrayList<String>();
		File hgtInv = new File(dataPath + "hgtInv.txt");
		BufferedWriter bw = new BufferedWriter(new FileWriter(hgtInv));
		while (line <= 420) {
			thisLine = invScanner.nextLine();
			boolean hasHGT = thisLine.contains(":HGT:");
			if (line >= 111 && hasHGT) {
				bw.append(thisLine);
				bw.newLine();
				hgtInvS.add(thisLine);
			}
			line++;
		}
		bw.close();
		invScanner.close();

		// use get_grib.exe to download a truncated grib2 file and write to
		// gribHGT.grib2 for parsing
		ProcessBuilder pb2 = new ProcessBuilder(getGribPath + "get_grib.exe", 
				grib2Path, dataPath + "gribHGT.grib2");
		pb2.redirectInput(hgtInv);
		pb2.redirectError(new File(dataPath + "getGribOut.txt"));
		Process process2 = pb2.start();
		try {
			process2.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// use wgrib2.exe to write p. alt across latLon to tmpHgts
		String[] wgribArgs = new String[3 * latLon.size() + 2];
		wgribArgs[0] = this.wgribPath;
		wgribArgs[1] = this.dataPath + "gribHGT.grib2";
		for (int i = 0; i < latLon.size(); i++) { // convert latLon to an array of string args
			wgribArgs[3 * i + 2] = "-lon";
			wgribArgs[3 * i + 3] = String.valueOf(latLon.get(i)[1]);
			wgribArgs[3 * i + 4] = String.valueOf(latLon.get(i)[0]);

		}
		List<String> tmpHgts = new ArrayList<String>();
		ProcessBuilder pb3 = new ProcessBuilder(wgribArgs);
		Process process3 = pb3.start();
		BufferedReader br = new BufferedReader(new InputStreamReader(process3.getInputStream()));
		String fileLine = null;
		while ((fileLine = br.readLine()) != null) {
			tmpHgts.add(fileLine);
		}

		// scan lines for first line where >= 1 value is <maxAlt
		line = 0;
		for (int i = 0; i < tmpHgts.size(); i++) {
			String[] heights = tmpHgts.get(i).split("val=");
			double maxMBAlt = 0;
			for (int j = 0; j < heights.length - 1; j++) {
				double alt = feetPerMeter * Double.parseDouble(heights[j].substring(0, heights[j].indexOf(':')));
				if (maxMBAlt < alt)
					maxMBAlt = alt;
			}
			double alt = Double.parseDouble(heights[heights.length - 1]);
			if (maxMBAlt < alt)
				maxMBAlt = alt;
			if (maxMBAlt < maxAlt) {
				line++;
			}
		}
		line = tmpHgts.size() - line;
		finalHGTs = new String[1 + tmpHgts.size() - line];
		//this is if maxAlt is below the 1000mb level, so only download 1000mb
		if (line == tmpHgts.size()) {
			String hgtString = hgtInvS.get(0).split(":HGT:")[1];
			String mbHeight = hgtString.substring(0, hgtString.indexOf(":"));
			finalHGTs[0] = mbHeight;
		}
		else {
			//this is if maxAlt is above the 150mb level, so download everything
			//same as if maxAlt was just below 150mb (line == 1)
			if (line == 0) {
				line++;
			}
			for (int i = line - 1; i < tmpHgts.size(); i++) {
				String hgtString = hgtInvS.get(i).split(":HGT:")[1];
				String mbHeight = hgtString.substring(0, hgtString.indexOf(":"));
				finalHGTs[i + 1 - line] = mbHeight;
			}
		}
		System.out.println("Got Height Data");
	}

	private void getInv() throws IOException {
		ProcessBuilder pb = new ProcessBuilder(getGribPath + "get_inv.exe", grib2Path + ".idx");
		File invOutput = new File(dataPath + "inv.txt");
		pb.redirectOutput(invOutput);
		Process process = pb.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// call after getPresAlt to ensure getInv has run
	// Takes mb alts in finalHGTs and returns TMP, UGRD, VGRD at those heights
	private void getWeatherGrib() throws IOException {
		System.out.println("Getting Full Weather Data");
		File queryInv = new File(dataPath + "queryInv.txt");
		BufferedWriter bw = new BufferedWriter(new FileWriter(queryInv));
		File inv = new File(dataPath + "inv.txt");
		BufferedReader br = new BufferedReader(new FileReader(inv));
		String thisLine = "";
		try {
			while ((thisLine = br.readLine()) != null) {
				// loop through all members of finalVars and finalHGTs to see if the line
				// matches both
				for (int i = 0; i < finalVars.length; i++) {
					if (thisLine.contains(finalVars[i])) {
						for (int j = 0; j < finalHGTs.length; j++) {
							if (thisLine.contains(finalHGTs[j])) {
								bw.append(thisLine);
								bw.newLine();
							}
						}
					}
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		bw.close();
		ProcessBuilder pb = new ProcessBuilder(getGribPath + "get_grib.exe", grib2Path, dataPath + "gribDATA.grib2");
		pb.redirectInput(queryInv);
		pb.redirectError(new File(dataPath + "getgribOut.txt"));
		Process process = pb.start();
		try {
			process.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Got Full Weather Data");
	}

	// returns [tmp][UGRD][VGRD][tDist] in C, temp, Kt, Kt, dist from standard at specified lat/lon/alt
	public double[] getWPWeather(double lat, double lon, int alt) throws IOException {
		// put hgt, tmp, and wind components at specified lat/lon into wpWeather
		List<Double> wpWeather = new ArrayList<Double>();
		ProcessBuilder pb = new ProcessBuilder(wgribPath, dataPath + "gribDATA.grib2",
				"-lon", Double.toString(lon), Double.toString(lat));
		Process process = pb.start();
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String fileLine = null;
		while ((fileLine = br.readLine()) != null) {
			wpWeather.add(Double.parseDouble(fileLine.split("val=")[1]));
		}
		
		// move last index of wpWeather to 4th from last, since data format is
		// inconsistent
		double lowestHGT = wpWeather.get(wpWeather.size() - 1);
		wpWeather.remove(wpWeather.size() - 1);
		wpWeather.add(wpWeather.size() - 3, lowestHGT);
		// read through wpWeather to find the heights which bracket alt
		double lastHeight = 0;
		int i;
		double altRatio = 0;
		for (i = 0; i < wpWeather.size(); i += 4) {
			double height = feetPerMeter*wpWeather.get(i);
			if (height < alt) {
				altRatio = (alt - height) / (lastHeight - height);
				break;
			}
			lastHeight = height;
		}
		// if alt is below 1000mb, use 1000mb as an approximation
		if (i == wpWeather.size()) i -= 4;
		double[] returnVal = new double[4];
		// if alt is above max, use max as an approximation
		if (i == 0) {
			returnVal[0] = wpWeather.get(i+1);
			returnVal[0] += KtoC;
			returnVal[1] = wpWeather.get(i + 2);
			returnVal[1] *= MStoKt;
			returnVal[2] = wpWeather.get(i + 3);
			returnVal[2] *= MStoKt;
			returnVal[3] = returnVal[0]-(15-alt/500);
		}
		else {
			returnVal[0] = altRatio * wpWeather.get(i - 3) + (1-altRatio)*wpWeather.get(i + 1);
			returnVal[0] += KtoC;
			returnVal[1] = altRatio * wpWeather.get(i - 2) + (1-altRatio)*wpWeather.get(i + 2);
			returnVal[1] *= MStoKt;
			returnVal[2] = altRatio * wpWeather.get(i - 1) + (1-altRatio)*wpWeather.get(i + 3);
			returnVal[2] *= MStoKt;
			returnVal[3] = returnVal[0]-(15-alt/500);
		}
		return returnVal;
	}
}