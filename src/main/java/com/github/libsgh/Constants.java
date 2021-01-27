package com.github.libsgh;

import cn.hutool.cache.impl.TimedCache;
import cn.hutool.core.date.DateUnit;

public class Constants {
	
	public static TimedCache<String, String> timedCache = new TimedCache<String, String>((long)(2*DateUnit.MINUTE.getMillis()));
	
	public static TimedCache<String, String> loginCache = new TimedCache<String, String>((long)(5*DateUnit.DAY.getMillis()));
	
}
