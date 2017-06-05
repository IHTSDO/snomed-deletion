package org.snomed.negative_delta;

public interface SnomedConstants {

	public static final String DELTA_SUFFIX = "_d";
	public static final String SNAPSHOT_SUFFIX = "_s";
	public static final String FULL_SUFFIX = "_f";
	
	public static final String[] TABLE_TYPES = new String[] { DELTA_SUFFIX, SNAPSHOT_SUFFIX, FULL_SUFFIX };
	
	public static final String DELTA = "Delta";
	public static final String SNAPSHOT = "Snapshot";
	public static final String FULL = "Full";
}
