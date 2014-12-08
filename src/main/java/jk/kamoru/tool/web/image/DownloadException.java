package jk.kamoru.tool.web.image;

public class DownloadException extends Exception {

	private static final long serialVersionUID = 4657698125060045244L;

	DownloadException(String url, String message) {
		super(String.format("%s - [%s]", message, url));
	}

	DownloadException(String url, String message, Throwable cause) {
		super(String.format("%s - [%s]", message, url), cause);
	}
	
}