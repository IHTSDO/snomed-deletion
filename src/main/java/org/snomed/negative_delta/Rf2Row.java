package org.snomed.negative_delta;

public class Rf2Row implements SnomedConstants, Comparable<Rf2Row> {
	String id;
	String row;
	Long effectiveTime;
	boolean isDeletion;
	
	public Rf2Row (String row, boolean isDeletion) {
		String[] fields = row.split(FIELD_DELIMITER);
		this.row = row;
		this.id = fields[IDX_ID];
		effectiveTime = Long.parseLong(fields[IDX_EFFECTIVETIME]);
		this.isDeletion = isDeletion;
		//For comparison purposes, we need the original row so reform it if this is a deletion row
		if (isDeletion) {
			StringBuffer sb = new StringBuffer();
			for (int idx=0; idx<fields.length; idx++) {
				if (idx != IDX_DEL_DELETIONEFFECTIVETIME && idx != IDX_DEL_DELETIONACTIVE) {
					if (idx>0) {
						sb.append(FIELD_DELIMITER);
					}
					sb.append(fields[idx]);
				}
			}
			this.row = sb.toString();
		}
	}
	
	String getId() {
		return id;
	}

	@Override
	public int compareTo(Rf2Row o) {
		return effectiveTime.compareTo(o.effectiveTime);
	}
	
	@Override
	public boolean equals (Object o) {
		if (o instanceof Rf2Row) {
			return row.equals(((Rf2Row)o).toString());
		}
		return false;
	}

	public Long getEffectiveTime() {
		return effectiveTime;
	}
	
	@Override
	public String toString() {
		return row;
	}
}
