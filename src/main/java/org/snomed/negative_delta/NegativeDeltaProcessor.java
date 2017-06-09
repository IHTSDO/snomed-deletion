package org.snomed.negative_delta;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.snomed.ApplicationException;
import org.snomed.util.GlobalUtils;

import com.google.common.io.Files;

public class NegativeDeltaProcessor implements SnomedConstants {

	File negativeDeltaArchive;
	File negativeDeltaLocation;
	File revisedDeletedStateRoot;
	File revisedDeletedStateLocation;
	
	File releaseArchive;
	File releaseLocation;
	File revisedReleaseRoot;
	File revisedReleaseLocation;

	String targetEffectiveTime;
	String edition = "INT";
	private static int MAX_THREADS = 4;
	
	List<String> childProcessesPending = new ArrayList<String>();
	List<String> childProcessesActive = new ArrayList<String>();
	Timestamp lastMsg = new Timestamp(System.currentTimeMillis());
	
	public static void main(String args[]) throws Exception{
		NegativeDeltaProcessor app = new NegativeDeltaProcessor();
		Timestamp startTime = new Timestamp(System.currentTimeMillis());
		System.out.println ("Started at " +  startTime);
		try{
			app.init(args);
			app.unzipFiles();
			app.processNegativeDelta();
			GlobalUtils.createArchive(app.revisedReleaseRoot);
			GlobalUtils.createArchive(app.revisedDeletedStateRoot);
		} finally {
			Timestamp now = new Timestamp(System.currentTimeMillis());
			System.out.println ("\nTime now " + now);
			System.out.println ("Time taken: " + timeDiff (startTime, now));
			app.cleanUp();
		}
	}

	private void unzipFiles() throws ApplicationException {
		negativeDeltaLocation = Files.createTempDir();
		print("Unzipping Negative Delta Archive " + negativeDeltaArchive.getAbsolutePath() + " to " + negativeDeltaLocation.getAbsolutePath());
		GlobalUtils.unzipFlat(negativeDeltaArchive, negativeDeltaLocation, new String[]{DELTA});
		
		releaseLocation = Files.createTempDir();
		print("Unzipping Release Archive " + releaseArchive.getAbsolutePath() + " to " + releaseLocation.getAbsolutePath());
		GlobalUtils.unzipFlat(releaseArchive, releaseLocation, new String[]{FULL});
	}

	private void init(String[] args) throws SQLException, ClassNotFoundException {
		if (args.length < 3) {
			print ("Usage NegativeDeltaProcessor <originalArchive> <negativeDeltaArchive> <targetEffectiveTime> <edition eg INT or US_1000024>");
			System.exit(-1);
		}
		
		releaseArchive = new File(args[0]);
		if (!releaseArchive.canRead()) {
			print ("Did not find valid negative delta archive - " + args[0]);
			System.exit(-1);
		}
		
		negativeDeltaArchive = new File(args[1]);
		if (!negativeDeltaArchive.canRead()) {
			print ("Did not find valid negative delta archive - " + args[1]);
			System.exit(-1);
		}
		
		targetEffectiveTime = args[2];
		long targetEffectiveTimeLong = Long.parseLong(targetEffectiveTime);
		if (targetEffectiveTime.length() != 8 || targetEffectiveTimeLong < 20010101 || targetEffectiveTimeLong > 20600731) {
			print ("Invalid target effective time: " +targetEffectiveTime);
			System.exit(-1);
		}
		edition = args[3];
		
		revisedReleaseRoot = Files.createTempDir();
		revisedReleaseLocation = new File (revisedReleaseRoot, "SnomedCT_" + edition + "_" + targetEffectiveTime);
		
		revisedDeletedStateRoot = Files.createTempDir();
		revisedDeletedStateLocation = new File (revisedDeletedStateRoot, "SnomedCT_NewState_" + edition + "_" + targetEffectiveTime);
	}

	private void processNegativeDelta() throws ApplicationException {
		//Work through each file in the release, pair it with a delta release
		//and send it off for asynchronous processing in a separate thread
		for (File fullFile : releaseLocation.listFiles()) {
			SnomedTable table = FileProcessor.identifyTable(fullFile.getName(), TableType.FULL);
			if (table == null) {
				print ("Skipping unrecognised file: " + fullFile, false);
			} else {
				File negativeDeltaFile = getNegativeDeltaFile(table);
				FileProcessor.processFile(negativeDeltaFile, 
						fullFile, 
						revisedReleaseLocation, 
						revisedDeletedStateLocation, 
						Long.parseLong(targetEffectiveTime), 
						this,
						edition);
			}
		}
		//Wait for all processes to complete
		try {
			do{
				Thread.sleep (5 * 1000);
			} while (childProcessesActive.size() > 0 || childProcessesPending.size() > 0);
		} catch (InterruptedException e) {
			throw new ApplicationException("Failed to wait for processes to complete",e);
		}
		print ("All parallel processes complete.", false);
	}

	private File getNegativeDeltaFile(SnomedTable table) {
		for (File f : negativeDeltaLocation.listFiles()) {
			if (f.getName().contains(table.getFilenamePart(TableType.DELTA))) {
				return f;
			}
		}
		return null;
	}

	private void print (String msg)  {
		print (msg, true);
	}
	
	private void print (String msg, boolean includeTiming)  {
		if (includeTiming) {
			//Add time taken onto the end of the last message
			Timestamp now = new Timestamp(System.currentTimeMillis());
			System.out.println (" (" + timeDiff(lastMsg, now) + ")");
			System.out.print(msg);
			lastMsg = now;
		} else {
			System.out.println(msg);
		}
	}
	
	private static String timeDiff (Timestamp earlier, Timestamp later) {
		int totalSecs = (int)(later.getTime() - earlier.getTime()) / 1000;
		int minutes = totalSecs / 60;
		int seconds = totalSecs % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private void cleanUp() {
		print("Cleaning up...");
		GlobalUtils.delete(negativeDeltaLocation);
		GlobalUtils.delete(releaseLocation);
		GlobalUtils.delete(revisedReleaseRoot);
		GlobalUtils.delete(revisedDeletedStateRoot);
	}

	public synchronized void registerChild(String tableName, boolean pending) {
		if (pending) {
			childProcessesPending.add(tableName);
		} else {
			print ("Starting process " + tableName, false);
			childProcessesPending.remove(tableName);
			childProcessesActive.add(tableName);
		}
	}

	public synchronized void releaseChild(String tableName, String result) {
		print (tableName + " process " + result, false);
		childProcessesActive.remove(tableName);
	}
	
	public synchronized boolean hasSpareCapacity() {
		return childProcessesActive.size() < MAX_THREADS;
	}
}
