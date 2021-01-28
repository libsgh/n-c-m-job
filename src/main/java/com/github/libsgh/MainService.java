package com.github.libsgh;

import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.druid.pool.DruidDataSource;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;
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
	
	public String getQr(String sid) {
		Long timestamp = System.currentTimeMillis();
		String result = HttpUtil.get(apiUrl + "/login/qr/key?timestamp=" + timestamp);
		if(JSONUtil.isJson(result)) {
			JSONObject jo = JSONUtil.parseObj(result);
			if(jo.getInt("code") == 200) {
				String unikey  = jo.getByPath("$.data.unikey", String.class);
				result = HttpUtil.get(apiUrl + "/login/qr/create?key="+unikey+"&qrimg=null&timestamp="+timestamp);
				Constants.timedCache.put(unikey, sid);
				asyncService.checkQr(unikey, sid);
				//Constants.loginCache.put(sid, "298158928");
				return JSONUtil.parseObj(result).getByPath("$.data.qrimg", String.class);
			}
		}
		return result;
	}

	public JSONObject getByUserId(String userId) {
		JSONObject jo = JSONUtil.createObj();
		try {
			Entity record = Db.use(ds).queryOne("select * from music_user where userId=? limit 1", userId);
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
	
}
