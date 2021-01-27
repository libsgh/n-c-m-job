package com.github.libsgh;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.alibaba.druid.pool.DruidDataSource;

import cn.hutool.core.date.DateUtil;
import cn.hutool.db.Db;
import cn.hutool.db.Entity;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;

@Service
public class Task {
	
	@Autowired
	private DruidDataSource ds;
	
	@Value("${api_url}")
	private String apiUrl;
	
	@Scheduled(cron = "0 0/5 * * * ?")
	public void kepp() {
		HttpUtil.get(apiUrl);
		HttpUtil.get("https://n-c-m-job.herokuapp.com");
	}
	
	@Scheduled(cron = "0 0/30 * * * ?")
	public void dailyTask() throws SQLException {
		Long time = DateUtil.parseDateTime(DateUtil.today()+" 00:00:00").getTime()/1000;
		//查询需要签到和听歌打卡用户
		List<Entity> list = Db.use(ds).query("SELECT " + 
				"	 *" + 
				" FROM" + 
				"	music_user" + 
				" WHERE 1=1" + 
				" and cookiestatus = 1 and (finishtime is null or finishtime <= ?)", time);
		for (Entity entity : list) {
			//检测登录状态
			String body = HttpRequest.get(apiUrl + "/login/status").cookie(entity.getStr("cookie")).execute().body();
			if(JSONUtil.parseObj(body).getInt("code") == 200) {
				//cookie有效
				if (entity.getInt("level") < 10) {
					//已经达到满级的不需要听歌啦
					Integer dailycount = 0;
					body = HttpRequest.get(apiUrl + "/recommend/resource?timestamp=" + System.currentTimeMillis()).cookie(entity.getStr("cookie")).execute().body();
					if(JSONUtil.parseObj(body).getInt("code") == 200) {
						JSONArray arr1 = JSONUtil.parseObj(body).getJSONArray("recommend");
						for (Object r : arr1) {
							JSONObject jo = (JSONObject)r;
							Integer resourceId = jo.getInt("id");
							String ss = HttpRequest.get(apiUrl + "/playlist/detail?id="+resourceId).cookie(entity.getStr("cookie")).execute().body();
							if(JSONUtil.parseObj(ss).getInt("code") == 200) {
								JSONArray arr = JSONUtil.parseObj(ss).getByPath("$.playlist.trackIds", JSONArray.class);
								for (Object ob : arr) {
									JSONObject songJo= (JSONObject)ob;
									Integer songId = songJo.getInt("id");
									String scrobble = HttpRequest.get(apiUrl + "/scrobble?id="+songId+"&sourceid="+resourceId).cookie(entity.getStr("cookie")).execute().body();
									if(JSONUtil.parseObj(scrobble).getInt("code") == 200 && JSONUtil.parseObj(ss).getStr("data").equals("success")) {
										dailycount++;
									}
								}
							}
						}
					}
					entity.set("dailycount", dailycount);
					entity.set("linstencount", entity.getInt("linstencount")+dailycount);
				}
				body = HttpRequest.get(apiUrl + "/daily_signin?timestamp=" + System.currentTimeMillis()).cookie(entity.getStr("cookie")).execute().body();
				if(JSONUtil.isJson(body) && JSONUtil.parseObj(body).getInt("code") == 200) {
					//签到成功
					Integer point = JSONUtil.parseObj(body).getInt("point");
					entity.set("dailysign", point);
				}else if(JSONUtil.isJson(body) && JSONUtil.parseObj(body).getInt("code") == -2){
					Log.get().info("重复签到");
					entity.set("dailysign", 3);
				}else{
					entity.set("dailysign", 0);
				}
			}else{
				entity.set("cookiestatus", 0);
			}
			Db.use(ds).update(entity, Entity.create("music_user").set("guid", entity.getStr("guid")));
		}
	}
	
}
