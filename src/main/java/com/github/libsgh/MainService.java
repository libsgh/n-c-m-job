package com.github.libsgh;

import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.druid.pool.DruidDataSource;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

@Service
public class MainService {
	
	@Value("${api_url}")
	private String apiUrl;
	
	@Autowired
	private AsyncService asyncService;
	
	@Autowired
	private DruidDataSource ds;
	
	public JSONObject getQr(String sid, String g) {
		JSONObject j = JSONUtil.createObj();
		Long timestamp = System.currentTimeMillis();
		String result = HttpUtil.get(apiUrl + "/login/qr/key?timestamp=" + timestamp);
		if(JSONUtil.isJson(result)) {
			JSONObject jo = JSONUtil.parseObj(result);
			if(jo.getInt("code") == 200) {
				String unikey  = jo.getByPath("$.data.unikey", String.class);
				result = HttpUtil.get(apiUrl + "/login/qr/create?key="+unikey+"&qrimg=null&timestamp="+timestamp);
				Constants.timedCache.put(unikey, sid);
				asyncService.checkQr(unikey, sid, g);
				String qrimg = JSONUtil.parseObj(result).getByPath("$.data.qrimg", String.class);
				j.set("qrimg", qrimg);
				j.set("unikey", unikey);
				j.set("sid", sid);
				return j;
				
			}
		}
		return j;
	}

	public JSONObject getByUserId(String userId) {
		JSONObject jo = JSONUtil.createObj();
		try {
			Entity record = Db.use(ds).queryOne("select * from music_user where userId=? limit 1", userId);
			record.set("finishtime", record.getLong("finishtime")*1000);
			if(record != null && !record.isEmpty()) {
				List<Entity> list = Db.use(ds).query("select * from music_user where groupid=?", record.getStr("groupId"));
				return jo.set("bean", record).set("mus", list);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return jo;
	}

	public JSONObject info() {
		JSONObject jo = JSONUtil.createObj();
		try {
			List<Entity> r6 = Db.use(ds).query("select * from music_user order by random() limit 6");
			Number count = Db.use(ds).queryNumber("select count(*) from music_user");
			return jo.set("count", count).set("r6", r6);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return jo;
	}

	public JSONObject pwdLogin(String sid, String loginname, String pwd, String g) throws SQLException {
		JSONObject jo = JSONUtil.createObj();
		Long timestamp = System.currentTimeMillis();
		String body = "";
		if(loginname.contains("@")) {
			//邮箱登录
			body = HttpRequest.get(apiUrl + "/login?timestamp=" + timestamp +" &email="+loginname+"&password="+pwd).execute().body();
		}else {
			body = HttpRequest.get(apiUrl + "/login/cellphone?timestamp=" + timestamp +" &phone="+loginname+"&md5_password="+SecureUtil.md5(pwd)).execute().body();
		}
		JSONObject jresult = JSONUtil.parseObj(body);
		if(jresult.getByPath("$.code",int.class) == 200) {
			//登录成功
			String cookie = jresult.getStr("cookie");
			Entity record = asyncService.getUserInfo(cookie, g);
			Entity tmp = Db.use(ds).queryOne("select * from music_user where userid=?", record.getStr("userid"));
			if(tmp != null && !tmp.isEmpty()) {
				//仅用于登录
			}else {
				Db.use(ds).insert(record);
			}
			Constants.loginCache.put(sid, record.getStr("userid"));
			jo.set("code", 200);
			jo.set("msg", "登录成功");
			jo.set("userid", record.getStr("userid"));
		}else {
			jo.set("code", -1);
			jo.set("msg", jresult.getByPath("$.msg",String.class));
		}
		return jo;
	}
	
}
