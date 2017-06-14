package org.snomed.negative_delta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.snomed.ApplicationException;
import org.snomed.util.GlobalUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Rf2File implements SnomedConstants{

	//Map of row items per identifier
	private Map<String, TreeSet<Rf2Row>> rows = new HashMap<String, TreeSet<Rf2Row>>();
	private File file;
	public static int MIN_LINE_LENGTH = 2;
	
	public Rf2File (File file) {
		this.file = file;
	}
	
	public void loadFile(boolean isDeletion) throws ApplicationException {
		if (file != null) {
			try {
				List<String> lines = Files.readLines(file, Charsets.UTF_8);
				boolean isHeaderRow = true;
				for (String line : lines) {
					if (!isHeaderRow && line.length() > MIN_LINE_LENGTH) {
						Rf2Row row = new Rf2Row(line, isDeletion);
						String id = row.getId();
						//Have we seen a row for this id before?
						TreeSet<Rf2Row> idRows = rows.get(id);
						if (idRows == null) {
							idRows = new TreeSet<Rf2Row>();
							rows.put(id, idRows);
						}
						idRows.add(row);
					} else {
						isHeaderRow = false;
					}
				}
			} catch (IOException e) {
				throw new ApplicationException("Failed to load " + file, e);
			}
		}
		if (isDeletion & file != null) {
			GlobalUtils.print ("Negative Delta for " + file.getName() + " referenced " + rows.size() + " components", true);
		}
	}
	
	public Set<String> getComponents() {
		return rows.keySet();
	}
	
	public TreeSet<Rf2Row> getComponentHistory(String id) {
		return rows.get(id);
	}

	public void removeRow(String id, Rf2Row fullRow) {
		TreeSet<Rf2Row> historyForComponent = rows.get(id);
		historyForComponent.remove(fullRow);
	}
}
