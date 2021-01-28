package com.github.libsgh;

import java.sql.SQLException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import com.alibaba.druid.pool.DruidDataSource;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.level.Level;

@SpringBootApplication
@Controller
@EnableAsync
@EnableScheduling
public class App {
	
	@Value("${DATABASE_URL}")
	private String dbUrl;
	
	@Autowired
	private MainService mainService;
	
	@Bean
	@Primary
	public DruidDataSource getDataSource() {
		String[] urls = dbUrl.split("/");
		DruidDataSource ds = new DruidDataSource();
		ds.setDriverClassName("org.postgresql.Driver");
		ds.setUrl("jdbc:postgresql://"+urls[2].split("@")[1]+"/"+urls[3]);
		ds.setUsername(urls[2].split("@")[0].split(":")[0]);
		ds.setPassword(urls[2].split("@")[0].split(":")[1]);
		return ds;
	}
	@Bean  
    public ServerEndpointExporter serverEndpointExporter() { 
        return new ServerEndpointExporter();  
    }  
	public static void main(String[] args) {
		DbUtil.setShowSqlGlobal(true, true, true, Level.INFO);
		DbUtil.setCaseInsensitiveGlobal(true);
		SpringApplication.run(App.class, args);
	}
	
	@GetMapping("/")
	public String index(Model model) throws SQLException {
		model.addAttribute("info", mainService.info());
		return "index";
	}
	
	@GetMapping("/login")
	public String login(Model model, HttpServletRequest request, HttpServletResponse response, @RequestParam(name = "g", required = false)String g) throws SQLException {
		String sid = Util.getSidFromCookie(request);
		if(StrUtil.isBlank(sid)) {
			if(StrUtil.isNotBlank(request.getParameter("sid"))) {
				sid = request.getParameter("sid");
			}else {
				sid = IdUtil.fastSimpleUUID();
			}
		}
		if(Constants.loginCache.containsKey(sid)) {
			String userId = Constants.loginCache.get(sid);
			Cookie sidCookie = new Cookie("sid", sid);
			sidCookie.setMaxAge(5 * 24 * 60 * 60);
			sidCookie.setPath(request.getContextPath());
			sidCookie.setHttpOnly(true);
			sidCookie.setDomain("n-c-m-job.herokuapp.com");
			response.addCookie(sidCookie);
			return "redirect:/task/"+userId;
		}
		//model.addAttribute("qrimg", mainService.getQr(sid));
		model.addAttribute("sid", sid);
		model.addAttribute("g", g);
		return "login";
	}
	
	@GetMapping("/api/qr")
	@ResponseBody
	public JSONObject getQr(String sid, Integer status, String unikey, @RequestParam(name = "g", required = false)String g) throws SQLException {
		JSONObject jo = JSONUtil.createObj();
		jo.set("sid", sid);
		if(status == 1) {
			//获取二维码
			jo = mainService.getQr(sid, g);
		}else{
			//使二维码失效
			Constants.timedCache.remove(unikey);
		}
		return jo;
	}
	@PostMapping("/api/pwdLogin")
	@ResponseBody
	public JSONObject pwdLogin(String sid, String loginname, String pwd, @RequestParam(name = "g", required = false)String g) throws SQLException {
		return mainService.pwdLogin(sid, loginname, pwd, g);
	}
	
	@GetMapping("/logout")
	public String logout(HttpServletRequest request, @RequestParam(name = "g", required = false)String g) {
		String sid = Util.getSidFromCookie(request);
		Constants.loginCache.remove(sid);
		if(StrUtil.isNotBlank(g)) {
			return "redirect:/login?g="+g;
		}else {
			return "redirect:/login";
		}
	}
	
	@GetMapping("/task/{userId}")
	public String task(Model model, HttpServletRequest request, @PathVariable("userId") String userId) throws SQLException {
		String sid = Util.getSidFromCookie(request);
		if(!Constants.loginCache.containsKey(sid)) { 
			//已经登录，重定向到首页
			return "redirect:/login";
		}else{
			JSONObject record = mainService.getByUserId(userId);
			if(record.isEmpty()) { 
				return "redirect:/login";
			}else{
				model.addAttribute("record", record); 
			} 
		}
		return "task";
	}

}
