/* Copyright 2019 hbz, Pascal Christoph. Licensed under the EPL 2.0*/

package helper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * Sends an email via localhost smtp. Make sure the server is configured
 * properly.
 * 
 * @author Pascal Christoph (dr0i)
 *
 */
public class Email {

	/**
	 * Sends an email.
	 * 
	 * @author Pascal Christoph (dr0i)
	 * 
	 * @param fromName the name before the "@" of an email
	 * @param toEmail the complete email address of the receiver
	 * @param subject the subject of the mail
	 * @param message the plain text message of the mail
	 */
	static public void sendEmail(final String fromName, final String toEmail,
			final String subject, final String message) {
		Properties prop = new Properties();
		prop.put("mail.smtp.auth", false);
		prop.put("mail.smtp.starttls.enable", "false");
		prop.put("mail.smtp.host", "127.0.0.1");
		prop.put("mail.smtp.port", "25");

		Session session = Session.getInstance(prop);

		Message msg = new MimeMessage(session);
		try {
			msg.setFrom(new InternetAddress(
					fromName + "@" + InetAddress.getLocalHost().getHostName()));
			msg.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(toEmail));
			msg.setSubject(subject);

			MimeBodyPart mimeBodyPart = new MimeBodyPart();
			mimeBodyPart.setContent(message, "text/plain");
			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(mimeBodyPart);

			msg.setContent(multipart);

			Transport.send(msg);
		} catch (MessagingException | UnknownHostException e) {
			e.printStackTrace();
		}
	}

}
