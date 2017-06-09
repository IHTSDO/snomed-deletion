package org.snomed.negative_delta;

public interface SnomedConstants {

	
	public static final String DELETION_PREFIX = "d";
	public static final String MODIFIER_PREFIX = "modified_";
	public static final String FIELD_DELIMITER = "\t";
	public static final String TYPE = "TYPE";
	
	public enum TableType { DELTA, SNAPSHOT, FULL };
	
	public static final String DELTA = "Delta";
	public static final String SNAPSHOT = "Snapshot";
	public static final String FULL = "Full";
	
	public static final int IDX_ID = 0; 
	public static final int IDX_EFFECTIVETIME = 1; 
	public static final int IDX_ACTIVE = 2; 
	public static final int IDX_MODULEID = 3; 
	
	public static final int IDX_DEL_ID = 0; 
	public static final int IDX_DEL_EFFECTIVETIME = 1; 
	public static final int IDX_DEL_DELETIONEFFECTIVETIME = 2; 
	public static final int IDX_DEL_ACTIVE = 3; 
	public static final int IDX_DEL_DELETIONACTIVE = 4; 
	public static final int IDX_DEL_MODULEID = 5; 
	
	public static final String ACTIVE = "1";
}
