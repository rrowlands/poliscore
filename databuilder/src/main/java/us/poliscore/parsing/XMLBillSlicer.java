package us.poliscore.parsing;

import static org.joox.JOOX.$;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.amazonaws.util.CollectionUtils;

import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;

public class XMLBillSlicer implements BillSlicer {

	protected Bill bill;
	
	private int sliceIndex = 0;
	
	private int maxSectionLength;
	
	@Override
	@SneakyThrows
	public List<BillSlice> slice(Bill bill, BillText btx, int maxSectionLength) {
		this.bill = bill;
		Node doc = (Node) toDoc(btx.getXml());
		
		val title = getTitle(doc);
		val body = $(doc).find("legis-body");
		if (StringUtils.isNotBlank(title) && body.isNotEmpty()) {
			doc = body.get().get(0);
		}
		
		this.maxSectionLength = maxSectionLength - title.length() - 1;
		
		val slices = divideAndConquer(doc);
		
		if (slices.size() == 1) return slices;
		
		int i = 0;
		for (val s : slices) {
			s.setText(title + s.getText());
			s.setSliceIndex(i++);
		}

		return slices;
	}
	
	protected String getTitle(Node doc) {
		val title = $(doc).find("metadata title");
		
		if (title.isEmpty()) {
			return "";
		}
		
		return "Bill title: " + title.text() + "\nSection content:\n";
	}
	
	protected List<BillSlice> divideAndConquer(Node node) {
		val text = $(node).text();
		
		if (text.length() < maxSectionLength) {
			return Arrays.asList(buildSlice($(node).xpath(), $(node).xpath(), text));
		} else if (node.getChildNodes().getLength() == 0 && text.length() >= maxSectionLength) {
			val slices = TextBillSlicer.slice(text);
			val list = new ArrayList<BillSlice>();
			slices.forEach(s -> list.add(buildSlice($(node).xpath(), $(node).xpath(), text)));
			return list;
		}
		
		val sections = $(node).children().map(c -> divideAndConquer(c.element())).stream().reduce(new ArrayList<BillSlice>(), (a,b) -> CollectionUtils.mergeLists(a, b));
		
		val result = new ArrayList<BillSlice>();
		int i = 0;
		while (i < sections.size()) {
			StringBuilder cur = new StringBuilder();
			String start = sections.get(i).getStart();
			
			while (i < sections.size() && cur.length() + sections.get(i).getText().length() < maxSectionLength-1) {
				if (cur.length() > 0) { cur.append("\\n"); }
				
				cur.append(sections.get(i).getText());
				i++;
			}
			
			result.add(buildSlice(start, sections.get(i-1).getEnd(), cur.toString()));
		}
		
		return result;
	}

	private BillSlice buildSlice(String xpathStart, String xpathEnd, String sectionText) {
		BillSlice slice = new BillSlice();
		slice.setBill(this.bill);
		slice.setText(sectionText);
		slice.setStart(xpathStart);
		slice.setEnd(xpathEnd);
		return slice;
	}
	
	@SneakyThrows
	public static Document toDoc(String xml) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setIgnoringElementContentWhitespace(true);
		factory.setValidating(false);
//		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
		factory.setFeature("http://xml.org/sax/features/namespaces", false);
		factory.setFeature("http://xml.org/sax/features/validation", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
		
		return doc;
	}

}
