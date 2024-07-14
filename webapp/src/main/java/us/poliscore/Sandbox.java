package us.poliscore;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import jakarta.ws.rs.PathParam;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.entrypoint.Lambda;
import us.poliscore.model.Legislator;
import us.poliscore.model.Persistable;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.IpGeolocationService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@QuarkusMain(name="Sandbox")
public class Sandbox implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
    IpGeolocationService ipService;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	protected void process() throws IOException
	{
//		val obj = dynamoDb.get("BIL/us/congress/118/hr/4763", Bill.class).orElseThrow();
//		
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(obj));
		
		
		
//		val legs = dynamoDb.query(Legislator.class, 25, null, null, null);
//		System.out.println(legs.size());
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(legs));
		
		
//		val leg = dynamoDb.get(Legislator.generateId(LegislativeNamespace.US_CONGRESS, "F000480"), Legislator.class);
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(leg));
		
		
//		getLegislatorPageData();
		
		
//		String sourceIp = "71.56.241.71";
//		val location = ipService.locateIp(sourceIp).orElse(null);
		String location = "CO";
    	val out = getLegislators(null, (location == null ? null : Persistable.OBJECT_BY_LOCATION_INDEX), null, null, location);
    	
    	
    	
    	System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(out));
	}
	
	public List<Legislator> getLegislators(Integer _pageSize, String _index, Boolean _ascending, String _exclusiveStartKey, String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	val legs = ddb.query(Legislator.class, pageSize, index, ascending, startKey, sortKey);
    	
    	legs.forEach(l -> l.setInteractions(new LegislatorBillInteractionSet()));
    	
    	return legs;
    }
	
	@SneakyThrows
	public void getLegislatorPageData() {
    	@SuppressWarnings("unchecked")
		List<List<String>> allLegs = PoliscoreUtil.getObjectMapper().readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/legislators.index"), "UTF-8"), List.class);
    	
    	System.out.println(PoliscoreUtil.getObjectMapper().writeValueAsString(allLegs));
    }
	
	public static void main(String[] args) {
		Quarkus.run(Sandbox.class, args);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
