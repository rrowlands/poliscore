package us.poliscore.model.legislator;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;

public class LegislatorInterpretationParser {

	private State state = null;
	private LegislatorInterpretation interp;

	public static enum State {
		SHORT_REPORT("(?i)Short Report:"),
		LONG_REPORT("(?i)Long Report:");

		private List<String> regex;

		private State(String... regex) {
			this.regex = Arrays.asList(regex);
		}
	}

	public LegislatorInterpretationParser(LegislatorInterpretation interp) {
		this.interp = interp;
	}

	public void parse(String text) {
		interp.setShortExplain("");
		interp.setLongExplain("");

		try (final Scanner scanner = new Scanner(text)) {
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine().strip();

				if (StringUtils.isBlank(line) || setState(line) || state == null)
					continue;

				processContent(line);
			}
		}
	}

	private void processContent(String line) {
		if (State.SHORT_REPORT.equals(state)) {
			interp.setShortExplain(interp.getShortExplain() + "\n" + line);
		} else if (State.LONG_REPORT.equals(state)) {
			interp.setLongExplain(interp.getLongExplain() + "\n" + line);
		}
	}

	private boolean setState(String line) {
		for (State s : State.values()) {
			for (String regex : s.regex) {
				if (line.matches(regex + ".*")) {
					state = s;

					// Handle inline content
					String inlineContent = line.replaceFirst(regex, "").strip();
					if (!inlineContent.isEmpty()) {
						processContent(inlineContent);
					}
					return true;
				}
			}
		}
		return false;
	}
}
