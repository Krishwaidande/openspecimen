package com.krishagni.catissueplus.core.common.email;

import com.krishagni.catissueplus.core.administrative.domain.User;

public class EmailSender {

	public Boolean sendUserCreatedEmail(final User user) {
		return EmailHandler.sendUserCreatedEmail(user);		
	}
	
	public Boolean sendForgotPasswordEmail(final User user, final String token) {
		return EmailHandler.sendForgotPasswordEmail(user, token);
	}
	
	public void sendUserSignupEmail(final User user) {
		EmailHandler.sendUserSignupEmail(user);
	}
}
