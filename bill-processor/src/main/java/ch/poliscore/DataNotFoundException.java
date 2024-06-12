package ch.poliscore;

public class DataNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 4905937432105155578L;

	public DataNotFoundException() {
		super();
	}

	public DataNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public DataNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

	public DataNotFoundException(String message) {
		super(message);
	}

	public DataNotFoundException(Throwable cause) {
		super(cause);
	}

}
