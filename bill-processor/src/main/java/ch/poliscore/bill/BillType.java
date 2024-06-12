package ch.poliscore.bill;

import lombok.Getter;

@Getter
public enum BillType {
	
	SCONRES("sconres"),
	
	HRES("hres"),
	
	HCONRES("hconres"),
	
	S("s"),
	
	SJRES("jsres"),
	
	SRES("sres"),
	
	HJRES("hjres"),
	
	HR("hr");
	
	private String name;
	
	private BillType(String name)
	{
		this.name = name;
	}
}
