package us.poliscore.service;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import us.poliscore.legiscan.service.LegiscanService;
import us.poliscore.model.bill.Bill;
import us.poliscore.service.storage.MemoryObjectService;

public class LegiscanImportService {
	
	@Inject
	private MemoryObjectService memService;
	
	private LegiscanService legiscan;
	
	@SneakyThrows
	public void importBills() {
		if (memService.query(Bill.class).size() > 0) return;
		
		long totalBills = 0;
		
		var masterList = legiscan.getMasterList(-1); 
		
		for (var bill : masterList.getBills().values())
		{
			
		}
		
//		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
//				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
//				.sorted((a,b) -> a.getName().compareTo(b.getName()))
//				.collect(Collectors.toList()))
//		{
//			if (!Integer.valueOf(PoliscoreUtil.CURRENT_SESSION.getNumber()).equals(Integer.valueOf(fCongress.getName()))) continue;
//			
//			val session = CongressionalSession.of(Integer.valueOf(fCongress.getName()));
//			
//			Log.info("Processing " + fCongress.getName() + " congress");
//			
//			for (val bt : BillService.PROCESS_BILL_TYPE)
//			{
//				Log.info("Processing bill types " + bt + " congress");
//				
//				for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills/" + bt), f -> f.getName().equals("data.json")))
//				{
//					try (var fos = new FileInputStream(data))
//					{
//						importUscBill(fos, String.valueOf(session.getNumber()));
//						totalBills++;
//					}
//				}
//			}
//		}
		
		Log.info("Imported " + totalBills + " bills");
	}
	
}
