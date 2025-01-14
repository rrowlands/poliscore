package us.poliscore.model;

import java.time.LocalDate;

import lombok.Getter;

@Getter
public enum CongressionalSession {
	S117(117, LocalDate.of(2021, 1, 3), LocalDate.of(2023, 1, 3)),
	S118(118, LocalDate.of(2023, 1, 3), LocalDate.of(2025, 1, 3));
	
	private int number;
	
	private LocalDate startDate;
	
	private LocalDate endDate;
	
	private CongressionalSession(int number, LocalDate startDate, LocalDate endDate) {
		this.number = number;
		this.startDate = startDate;
		this.endDate = endDate;
	}
	
	public static CongressionalSession of(Integer session)
	{
		if (session == 118) {
			return S118;
		} else if (session == 117) {
			return S117;
		} else {
			throw new UnsupportedOperationException("Unsupported session [" + String.valueOf(session) + "].");
		}
	}

	public boolean isOver() {
		return LocalDate.now().isAfter(endDate);
	}
}
