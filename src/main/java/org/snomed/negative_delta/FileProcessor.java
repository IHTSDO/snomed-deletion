package org.snomed.negative_delta;

import static org.snomed.util.GlobalUtils.print;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.snomed.ApplicationException;
import org.snomed.util.GlobalUtils;

public class FileProcessor implements Runnable, SnomedConstants {
	
	private Rf2File negativeDelta;
	private Rf2File fullFile;
	private File revisedReleaseLocation;
	private File revisedDeletedStateLocation;
	private Long targetEffectiveTime;
	private SnomedTable table;
	NegativeDeltaProcessor parent;
	String edition;
	Set<String> allComponents;
	
	private FileProcessor() {
	}
	
	public static void processFile (File negativeDelta, 
			File fullFile, 
			File revisedReleaseLocation, 
			File revisedDeletedStateLocation, 
			Long targetEffectiveTime,
			NegativeDeltaProcessor parent,
			String edition) {
		FileProcessor fp = new FileProcessor();
		fp.negativeDelta = new Rf2File(negativeDelta);
		fp.fullFile = new Rf2File(fullFile);
		fp.revisedReleaseLocation = revisedReleaseLocation;
		fp.revisedDeletedStateLocation = revisedDeletedStateLocation;
		fp.targetEffectiveTime = targetEffectiveTime;
		fp.table = identifyTable(fullFile.getName(), TableType.FULL);
		fp.parent = parent;
		if (fp.table == null) {
			print ("Unable to process unrecognised file :" + fullFile.getName());
			return;
		}
		fp.edition = edition;
		Thread thread = new Thread(fp, fp.table.getTableName());
		thread.start();
	}

	@Override
	public void run() {
		parent.registerChild(table.getTableName(), true); //Register as pending, until we have spare capacity
		String result = "processing incomplete";
		try {
			//Register with the parent - when it's able
			while (!parent.hasSpareCapacity()) {
				Thread.sleep(5 * 1000);
			}
			parent.registerChild(table.getTableName(), false);
			negativeDelta.loadFile(true);
			fullFile.loadFile(false);
			allComponents = fullFile.getComponents();
			removeDeltaFromFull();
			removeLaterEffectiveTime();
			outputFull();
			outputSnapshot();
			outputDelta();
			outputNewStateOfDeletedComponents();
			result = "processing complete";
		} catch (Exception e) {
			result = "failed due to " + e.getMessage();
			e.printStackTrace();
		} finally {
			parent.releaseChild(table.getTableName(), result);
		}
	}

	private void removeDeltaFromFull() {
		//Loop through the active deletions and remove them from the full
		//TODO Check for inactive deletions
		int rowsRemoved = 0;
		for (String id : negativeDelta.getComponents()) {
			for(Rf2Row deletionRow : negativeDelta.getComponentHistory(id).descendingSet()) {
				Set<Rf2Row> fullRows = new HashSet<Rf2Row>(fullFile.getComponentHistory(id).descendingSet()); //Copy so we can modify original
				for(Rf2Row fullRow : fullRows) {
					if (fullRow.equals(deletionRow)) {
						fullFile.removeRow(id, fullRow);
						rowsRemoved++;
					}
				}
			}
		}
		if (rowsRemoved > 0) {
			print (rowsRemoved + " rows removed in " + table.getTableName());
		}
	}
	
	private void removeLaterEffectiveTime() {
		//Loop through the full file and remove effective times > targetEffectiveTime
		for (String id : fullFile.getComponents()) {
			for(Rf2Row fullRow : fullFile.getComponentHistory(id).descendingSet()) {
				if (fullRow.getEffectiveTime() > targetEffectiveTime) {
					fullFile.removeRow(id, fullRow);
				}
			}
		}
	}

	private void outputFull() throws ApplicationException {
		PrintWriter fullOutput = prepareFile(revisedReleaseLocation, TableType.FULL);
		export(allComponents, fullOutput, TableType.FULL);
	}

	private void outputSnapshot() throws ApplicationException {
		PrintWriter snapOutput = prepareFile(revisedReleaseLocation, TableType.SNAPSHOT);
		export(allComponents, snapOutput, TableType.FULL);
	}

	private void outputDelta() throws ApplicationException {
		PrintWriter deltaOutput = prepareFile(revisedReleaseLocation, TableType.DELTA);
		export(allComponents, deltaOutput, TableType.DELTA);
	}
	
	private void outputNewStateOfDeletedComponents() throws ApplicationException {
		PrintWriter snapDeletedOutput = prepareFile(revisedDeletedStateLocation, TableType.SNAPSHOT);
		Set<String> affectedComponents = negativeDelta.getComponents();
		if (affectedComponents.size() > 0) {
			print("New state calculated in " + table.getTableName());
		}
		export(affectedComponents, snapDeletedOutput, TableType.SNAPSHOT);
	}

	public static SnomedTable identifyTable(String fileName, TableType tableType) {
		for (SnomedTable table : SnomedTable.SnomedTables) {
			String fileNamePart = table.getFilenamePart().replace(TYPE, SnomedTable.getFileType(tableType));
			if (fileName.contains(fileNamePart)) {
				return table;
			}
		}
		print ("Unable to determine table for file " + fileName + " skipping...");
		return null;
	}
	
	private PrintWriter prepareFile(File exportLocation, TableType tableType) throws ApplicationException {
		try {
			String fileName = table.getFilename(edition, targetEffectiveTime.toString(), tableType);
			File outputFile = new File (exportLocation, fileName);
			print ("Outputting to " + outputFile);
			GlobalUtils.ensureFileExists(outputFile.getAbsolutePath());
			PrintWriter out = GlobalUtils.prepareFileToWrite(outputFile);
			//Write the header line
			GlobalUtils.writeToFile(out, Collections.singletonList(table.getFileHeader()));
			return out;
		} catch (Exception e) {
			throw new ApplicationException("Failed to prepare file for " + tableType, e);
		}
	}

	private synchronized void export(Set<String> components, PrintWriter out, TableType tableType) throws ApplicationException {

		for (String id : components) {
			switch (tableType) {
				case FULL : //Output all remaining rows for all components
					GlobalUtils.writeToFile(out,fullFile.getComponentHistory(id).descendingSet());
					break;
				case SNAPSHOT : //Output the most recent row for all components
					String rowStr = fullFile.getComponentHistory(id).descendingSet().first().toString();
					//Check for two rows with the same effectiveTime
					checkForAmbiguity(id);
					GlobalUtils.writeToFile(out,Collections.singletonList(rowStr));
					break;
				case DELTA : //Output the most recent row IF it has the target effective Time
					Rf2Row row = fullFile.getComponentHistory(id).descendingSet().first();
					if (row.getEffectiveTime() == targetEffectiveTime) {
						GlobalUtils.writeToFile(out,Collections.singletonList(row.toString()));
					}
			}
		}
		out.close();
	}

	//Check to see if the most recent state is ambiguous ie two rows for the same effectiveTime
	private void checkForAmbiguity(String id) {
		Iterator<Rf2Row> rows = fullFile.getComponentHistory(id).iterator();
		Rf2Row first = rows.next();
		if (rows.hasNext()) {
			Rf2Row second = rows.next();
			if (first.getEffectiveTime() == second.getEffectiveTime()) {
				print ("** Ambiguity in " + fullFile + ": " + first.toString());
			}
		}
	}
}
