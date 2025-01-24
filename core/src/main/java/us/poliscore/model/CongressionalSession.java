package us.poliscore.model;

import java.time.LocalDate;
import java.util.Arrays;

import lombok.Getter;

@Getter
public enum CongressionalSession {
	S117(117, LocalDate.of(2021, 1, 3), LocalDate.of(2023, 1, 3)),
	S118(118, LocalDate.of(2023, 1, 3), LocalDate.of(2025, 1, 3)),
	S119(119, LocalDate.of(2025, 1, 3), LocalDate.of(2027, 1, 3));
	
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
		return Arrays.asList(CongressionalSession.values()).stream().filter(s -> Integer.valueOf(s.getNumber()).equals(session)).findAny().orElseThrow();
	}
	
	public static CongressionalSession fromYear(Integer year)
	{
		return of((int) Math.floor((year - 1789) / 2) + 1);
	}

	public boolean isOver() {
		return LocalDate.now().isAfter(endDate);
	}
}
