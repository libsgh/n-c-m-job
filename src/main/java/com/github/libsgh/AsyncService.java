package com.github.libsgh;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.alibaba.druid.pool.DruidDataSource;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

@Service
public class AsyncService {
	
	@Value("${api_url}")
	private String apiUrl;
	
	@Autowired
	private DruidDataSource ds;
	
	@Async
	public void checkQr(String unikey, String sid, String g) {
		Long timestamp = System.currentTimeMillis();
		if(Constants.timedCache.containsKey(unikey)) {
			String result = HttpUtil.get(apiUrl + "/login/qr/check?key=" + unikey+"&timestamp="+timestamp);
			JSONObject jo = JSONUtil.parseObj(result);
			Integer status = JSONUtil.parseObj(result).getByPath("$.code", Integer.class);
			System.out.println(status);
			//800为二维码过期,801为等待扫码,802为待确认,803为授权登录成功(803状态码下会返回cookies)
			if(status.intValue() == 801) {
				//等待扫码
				this.checkQr(unikey, sid, g);
			}else if(status.intValue() == 802) {
				//待确认
				this.checkQr(unikey, sid, g);
			}else if(status.intValue() == 803) {
				//授权登录成功
				jo.getStr("cookie");
				try {
					Entity record = getUserInfo(jo.getStr("cookie"), g);
					Entity tmp = Db.use(ds).queryOne("select * from music_user where userid=?", record.getStr("userid"));
					if(tmp != null && !tmp.isEmpty()) {
						//仅用于登录
					}else {
						Db.use(ds).insert(record);
					}
					jo.set("userid", record.getStr("userid"));
					Constants.loginCache.put(sid, record.getStr("userid"));
				} catch (SQLException e) {
					e.printStackTrace();
				}
				Constants.timedCache.remove(unikey);
				WebSocketServer.sendInfo(jo.toString(), sid);
			}else{
				Constants.timedCache.remove(unikey);
				WebSocketServer.sendInfo(jo.toString(), sid);
			}
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	public Entity getUserInfo(String cookie, String g) {
		String guid = IdUtil.fastSimpleUUID();
		Entity record = Entity.create("music_user")
						.set("guid", guid);
		String body = HttpRequest.get(apiUrl + "/user/account").cookie(cookie).execute().body();
		JSONObject jo = JSONUtil.parseObj(body);
		String nickname = jo.getByPath("$.profile.nickname", String.class);
		String userId = jo.getByPath("$.profile.userId", String.class);
		String avatarUrl = jo.getByPath("$.profile.avatarUrl", String.class);
		String signature = jo.getByPath("$.profile.signature", String.class);
		body = HttpRequest.get(apiUrl + "/user/level").cookie(cookie).execute().body();
		jo = JSONUtil.parseObj(body);
		Integer progress = jo.getByPath("$.data.progress", Integer.class);
		Integer nowLoginCount = jo.getByPath("$.data.nowLoginCount", Integer.class);
		Integer nowPlayCount = jo.getByPath("$.data.nowPlayCount", Integer.class);
		Integer nextLoginCount = jo.getByPath("$.data.nextLoginCount", Integer.class);
		Integer nextPlayCount = jo.getByPath("$.data.nextPlayCount", Integer.class);
		Integer level = jo.getByPath("$.data.level", Integer.class);
		record.set("uname", nickname);
		record.set("userid", userId);
		record.set("avatarurl", avatarUrl);
		record.set("progress", progress);
		record.set("nowlogincount", nowLoginCount);
		record.set("nowplaycount", nowPlayCount);
		record.set("nextlogincount", nextLoginCount);
		record.set("nextplaycount", nextPlayCount);
		record.set("level", level);
		record.set("isvip", 1);
		record.set("createtime", System.currentTimeMillis());
		record.set("cookiestatus", 1);
		record.set("cookie", cookie);
		if(StrUtil.isNotBlank(g)) {
			record.set("groupid", g);
		}else{
			record.set("groupid", guid);
		}
		record.set("signature", signature);
		record.set("linstencount", 0);
		record.set("dailycount", 0);
		record.set("dailysign", 0);
		body =  HttpRequest.get(apiUrl + "/user/detail?uid="+userId).cookie(cookie).execute().body();
		jo = JSONUtil.parseObj(body);
		Integer eventCount = jo.getByPath("$.profile.eventCount", Integer.class);
		Integer followeds = jo.getByPath("$.profile.followeds", Integer.class);
		Integer follows = jo.getByPath("$.profile.follows", Integer.class);
		record.set("eventcount", eventCount);
		record.set("followeds", followeds);
		record.set("follows", follows);
		return record;
	}
}
