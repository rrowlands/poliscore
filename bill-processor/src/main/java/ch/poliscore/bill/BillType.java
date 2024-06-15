package ch.poliscore.bill;

import java.util.Arrays;
import java.util.List;

import lombok.Getter;

@Getter
public enum BillType {
	
	SCONRES("sconres"),
	
	HRES("hres"),
	
	HCONRES("hconres"),
	
	S("s"),
	
	SJRES("sjres"),
	
	SRES("sres"),
	
	HJRES("hjres"),
	
	HR("hr");

	
	private String name;
	
	private BillType(String name)
	{
		this.name = name;
	}
	
	public static List<BillType> getIgnoredBillTypes()
	{
		return Arrays.asList(BillType.SCONRES, BillType.HCONRES, BillType.HRES, BillType.SRES);
	}
}
