package us.poliscore.parsing;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import lombok.SneakyThrows;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillSlice;

public class XMLBillSlicer implements BillSlicer {

	private int sliceIndex = 0;
	
	@Override
	@SneakyThrows
	public List<BillSlice> slice(Bill bill) {
		final List<BillSlice> slices = new ArrayList<BillSlice>();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setIgnoringElementContentWhitespace(true);
		factory.setValidating(false);
//		factory.setNamespaceAware(true);
		factory.setFeature("http://xml.org/sax/features/namespaces", false);
		factory.setFeature("http://xml.org/sax/features/validation", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new ByteArrayInputStream(bill.getText().getXml().getBytes("UTF-8")));

		StringBuilder cur = new StringBuilder();
		int start = 0;

		List<Node> sections = childNodesWhere(doc, true, n -> n.getNodeName().equals("section"));
		for (int i = 0; i < sections.size(); ++i) {
			Node section = sections.get(i);

			String sectionText = String.join(" ", childNodesWhere(section, true, n -> n.getNodeType() == Node.TEXT_NODE)
					.stream().map(n -> n.getTextContent()).collect(Collectors.toList()));
			
			if (cur.length() > 0 && cur.length() + sectionText.length() >= BillSlicer.MAX_SECTION_LENGTH)
			{
				slices.add(buildSlice(bill, start, i, cur.toString()));
				start = i;
				cur = new StringBuilder();
			}

			cur.append(sectionText);
		}
		
		if (cur.length() > 0)
		{
			slices.add(buildSlice(bill, start, sections.size() - 1, cur.toString()));
		}

		return slices;
	}

	private BillSlice buildSlice(Bill bill, int start, int end, String sectionText) {
		BillSlice slice = new BillSlice();
		slice.setBill(bill);
		slice.setText(sectionText);
		slice.setSectionStart(start);
		slice.setSectionEnd(end);
		slice.setSliceIndex(sliceIndex++);
		return slice;
	}

	private List<Node> childNodesWhere(Node n, boolean recursive, Predicate<? super Node> criteria) {
		List<Node> list = new ArrayList<Node>();
		NodeList nl = n.getChildNodes();

		for (int i = 0; i < nl.getLength(); ++i) {
			Node cn = nl.item(i);

			list.add(cn);

			if (recursive)
				list.addAll(childNodesWhere(cn, recursive, criteria));
		}

		if (criteria == null)
			return list;

		return list.stream().filter(criteria).collect(Collectors.toList());
	}

}
