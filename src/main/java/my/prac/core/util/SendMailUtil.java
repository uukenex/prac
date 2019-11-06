package my.prac.core.util;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendMailUtil extends javax.mail.Authenticator {

	static Logger logger = LoggerFactory.getLogger(SendMailUtil.class);

	// 이메일로 id찾기
	public void email_Password(String email, String pass) {
		String host = "smtp.gmail.com";
		String subject = "여행을 부탁해 비밀번호 입니다.";
		String from = "zzxx4949@gmail.com"; // 보내는 메일
		String fromName = "여행을 부탁해";
		String to = "" + email;
		StringBuffer sb = new StringBuffer();

		logger.trace("보낼 메일 주소 : {}", email);

		sb.append("문의하신 아이디의  임시 비밀번호는 <br/>").append(pass + " 입니다.<br/>").append("비밀번호를 꼭 변경 해 주시길 바랍니다.");

		try {
			// 프로퍼티 값 인스턴스 생성과 기본세션(SMTP 서버 호스트 지정)
			Properties props = new Properties();
			// 네이버 SMTP 사용시
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.transport.protocol", "smtp");
			props.put("mail.smtp.host", host);

			props.put("mail.smtp.port", "465"); // 보내는 메일 포트 설정
			props.put("mail.smtp.user", from);
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.debug", "true");
			props.put("mail.smtp.socketFactory.port", "465");
			props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			props.put("mail.smtp.socketFactory.fallback", "false");

			Authenticator auth = new SendMailUtil();
			Session mailSession = Session.getDefaultInstance(props, auth);

			Message msg = new MimeMessage(mailSession);
			msg.setFrom(new InternetAddress(from, MimeUtility.encodeText(fromName, "UTF-8", "B"))); // 보내는
																									// 사람
																									// 설정
			InternetAddress[] address = { new InternetAddress(to) };

			msg.setRecipients(Message.RecipientType.TO, address); // 받는 사람설정

			msg.setSubject(subject); // 제목설정
			msg.setSentDate(new java.util.Date()); // 보내는 날짜 설정
			msg.setContent(sb.toString(), "text/html; charset=UTF-8"); // 내용
																		// 설정(MIME
																		// 지정-HTML
																		// 형식)

			Transport.send(msg); // 메일 보내기

			System.out.println("메일 발송을 완료하였습니다.");
		} catch (MessagingException ex) {
			System.out.println("mail send error : " + ex.getMessage());
			ex.printStackTrace();
		} catch (Exception e) {
			System.out.println("error : " + e.getMessage());
			e.printStackTrace();
		}
	}

	public PasswordAuthentication getPasswordAuthentication() {

		// 네이버나 Gmail 사용자 계정 설정.
		// Gmail의 경우 @gmail.com을 제외한 아이디만 입력한다.
		return new PasswordAuthentication("travelgogosing", "a1568476");
	}

}