package ch.poliscore.interpretation;

import org.apache.commons.io.FilenameUtils;

import lombok.val;

public enum BillTextPublishVersion {
	/// Important! ///
	// The ordering of the enums here is used to determine which bill text to select //
	///////
	
	// Introduced
	IH,
	IS,

	// Amended
	AS,
	ASH,
	EAH,
	EAS,

	// Bill in deliberation
	ATH,
	ATS,
	CPH,
	CPS,
	EH,
	EPH,
	ES,
	HDH,
	HDS,
	OPH,
	OPS,
	PAV,
	PCH,
	PCS,
	PP,
	PWAH,
	RAH,
	RAS,
	RCH,
	RCS,
	RDH,
	RDS,
	REAH,
	RES,
	RENR,
	RFH,
	RFS,
	RH,
	RHUC,
	RIH,
	RIS,
	RS,
	RTH,
	RTS,
	SAS,
	SC,

	// Headed to President
	ENR,

	// Finalized (success)
	PAP,

	// Finalized (thrown out)
	CDH,
	CDS,
	FAH,
	FPH,
	FPS,
	IPH,
	IPS,
	LTH,
	LTS;
	
	public static BillTextPublishVersion parseFromBillTextName(String fileName)
	{
		for (BillTextPublishVersion v : BillTextPublishVersion.values())
		{
			val bn = FilenameUtils.getBaseName(fileName);
			
			if (bn.endsWith(v.name().toLowerCase()) || bn.endsWith(v.name().toUpperCase()))
			{
				return v;
			}
		}
		
		return null;
	}

	/**
	 * Can be used to sort bill publish versions based on the maturity of a bill
	 * 
	 * @param fromBillTextName
	 * @return
	 */
	public int billMaturityCompareTo(BillTextPublishVersion fromBillTextName) {
		return this.compareTo(fromBillTextName);
	}
}