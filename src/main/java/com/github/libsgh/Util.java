package com.github.libsgh;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

public class Util {
	
	public static String getSidFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		String sid = null;
		if(cookies == null) {
			return sid;
		}
		for (Cookie cookie : cookies) {
			if("sid".equals(cookie.getName())) {
				sid = cookie.getValue();
				break;
			}
		}
		return sid;
	}
	
}
