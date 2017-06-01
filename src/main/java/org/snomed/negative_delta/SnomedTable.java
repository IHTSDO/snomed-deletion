package org.snomed.negative_delta;

import java.util.ArrayList;
import java.util.List;

public class SnomedTable {
	
	static List<SnomedTable> SnomedTables = new ArrayList<SnomedTable>();
	static {
		String termDir = "Delta/Terminology/";
		String refDir =  "Delta/Refset/";
		SnomedTables.add(new SnomedTable("concept_f","sct2_Concept_Delta", termDir + "sct2_Concept_Delta_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("description_f","sct2_Description_Delta",  termDir + "sct2_Description_Delta-LNG_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("textdefinition_f","sct2_TextDefinition_Snapshot", termDir + "sct2_TextDefinition_Snapshot-LNG_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("langrefset_f","der2_cRefset_LanguageDelta", refDir + "Language/der2_cRefset_LanguageDelta-LNG_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("relationship_f","sct2_Relationship_Delta",termDir + "sct2_Relationship_Delta_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("stated_relationship_f","sct2_StatedRelationship_Delta", termDir + "sct2_StatedRelationship_Delta_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("simplerefset_f","", termDir + "Content/der2_Refset_SimpleDelta_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("associationrefset_f","der2_cRefset_AssociationReferenceSnapshot",  refDir + "Content/der2_cRefset_AssociationReferenceSnapshot_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("attributevaluerefset_f","der2_cRefset_AttributeValueDelta", refDir + "Content/der2_cRefset_AttributeValueDelta_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("extendedmaprefset_f","der2_iisssccRefset_ExtendedMapSnapshot", refDir + "Map/der2_iisssccRefset_ExtendedMapSnapshot_EDITION_DATE.txt"));
		SnomedTables.add(new SnomedTable("simplemaprefset_f","der2_sRefset_SimpleMap", refDir + "Map/der2_sRefset_SimpleMapDelta_EDITION_DATE.txt"));
	}
	
	private String tableName;
	private String filenamePart;
	private String filenameTemplate;
	
	public SnomedTable (String table, String filenamePart, String filenameTemplate) {
		this.tableName = table;
		this.filenamePart = filenamePart;
		this.filenameTemplate = filenameTemplate;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String getFilenamePart() {
		return filenamePart;
	}

	public void setFilename(String filenamePart) {
		this.filenamePart = filenamePart;
	}

	public String getFilenameTemplate() {
		return filenameTemplate;
	}

	public void setFilenameTemplate(String filenameTemplate) {
		this.filenameTemplate = filenameTemplate;
	}
}
