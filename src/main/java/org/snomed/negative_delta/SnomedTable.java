package org.snomed.negative_delta;

import java.util.ArrayList;
import java.util.List;

public class SnomedTable implements SnomedConstants {
	
	static List<SnomedTable> SnomedTables = new ArrayList<SnomedTable>();
	static {
		String termDir = "TYPE/Terminology/";
		String refDir =  "TYPE/Refset/";
		SnomedTables.add(new SnomedTable("concept","sct2_Concept_TYPE", 
				termDir + "sct2_Concept_TYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tdefinitionStatusId"));
		SnomedTables.add(new SnomedTable("description","sct2_Description_TYPE",  
				termDir + "sct2_Description_TYPE-LNG_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId"));
		SnomedTables.add(new SnomedTable("textdefinition","sct2_TextDefinition_Snapshot", 
				termDir + "sct2_TextDefinition_Snapshot-LNG_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tconceptId\tlanguageCode\ttypeId\tterm\tcaseSignificanceId"));
		SnomedTables.add(new SnomedTable("langrefset","der2_cRefset_LanguageTYPE", 
				refDir + "Language/der2_cRefset_LanguageTYPE-LNG_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tacceptabilityId"));
		SnomedTables.add(new SnomedTable("relationship","sct2_Relationship_TYPE",
				termDir + "sct2_Relationship_TYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId"));
		SnomedTables.add(new SnomedTable("stated_relationship","sct2_StatedRelationship_TYPE", 
				termDir + "sct2_StatedRelationship_TYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId"));
		SnomedTables.add(new SnomedTable("simplerefset","der2_Refset_SimpleTYPE", 
				refDir + "Content/der2_Refset_SimpleTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId"));
		SnomedTables.add(new SnomedTable("associationrefset","der2_cRefset_AssociationReferenceTYPE",  
				refDir + "Content/der2_cRefset_AssociationReferenceSnapshot_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\ttargetComponentId"));
		SnomedTables.add(new SnomedTable("attributevaluerefset","der2_cRefset_AttributeValueTYPE", 
				refDir + "Content/der2_cRefset_AttributeValueTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tvalueId"));
		SnomedTables.add(new SnomedTable("extendedmaprefset","der2_iisssccRefset_ExtendedMapTYPE", 
				refDir + "Map/der2_iisssccRefset_ExtendedMapTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapGroup\tmapPriority\tmapRule\tmapAdvice\tmapTarget\tcorrelationId\tmapCategoryId"));
		SnomedTables.add(new SnomedTable("refsetDescriptor", "der2_cciRefset_RefsetDescriptorTYPE",
				refDir + "Metadata/der2_cciRefset_RefsetDescriptorTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tattributeDescription\tattributeType\tattributeOrder"));
		SnomedTables.add(new SnomedTable("descriptionType", "der2_ciRefset_DescriptionTypeTYPE",
				refDir + "Metadata/der2_ciRefset_DescriptionTypeTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tdescriptionFormat\tdescriptionLength"));
		
		SnomedTables.add(new SnomedTable("simplemaprefset","der2_sRefset_SimpleMapTYPE", 
				refDir + "Map/der2_sRefset_SimpleMapTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapTarget"));
	}
	
	private String tableName;
	private String filenamePart;
	private String filenameTemplate;
	private String fileHeader;
	
	public SnomedTable (String tableName, String filenamePart, String filenameTemplate, String fileHeader) {
		this.tableName = tableName;
		this.filenamePart = filenamePart;
		this.filenameTemplate = filenameTemplate;
		this.fileHeader = fileHeader;
	}

	public String getTableName() {
		return tableName;
	}

	public String getFilenamePart() {
		return filenamePart;
	}

	public String getFilenameTemplate() {
		return filenameTemplate;
	}

	public String getFileHeader() {
		return fileHeader;
	}

	public String getFilename(String edition, String targetEffectiveTime,
			TableType tableType) {
		return filenameTemplate.replace("EDITION", edition).
				replace("DATE", targetEffectiveTime).
				replaceAll(TYPE, getFileType(tableType));
	}
	
	public static String getFileType(TableType tableType) {
		switch (tableType) {
			case DELTA : return DELTA;
			case SNAPSHOT : return SNAPSHOT;
			case FULL : 
			default:return FULL;
		}
	}

	//Returns 
	public String getFilenamePart(TableType tableType) {
		return filenamePart.replace(TYPE, getFileType(tableType));
	}
}
