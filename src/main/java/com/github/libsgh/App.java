package com.github.libsgh;

import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;

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
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import com.alibaba.druid.pool.DruidDataSource;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.json.JSONObject;
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
	public String login(Model model, HttpServletRequest request) throws SQLException {
		String sid = (String) request.getSession().getAttribute("sid");
		if(StrUtil.isBlank(sid)) {
			sid = IdUtil.fastSimpleUUID();
			request.getSession().setAttribute("sid", sid);
		}
		if(Constants.loginCache.containsKey(sid)) {
			//已经登录，重定向到首页
			String userId = Constants.loginCache.get(sid);
			return "redirect:/task/"+userId;
		}
		model.addAttribute("qrimg", mainService.getQr(sid));
		model.addAttribute("sid", sid);
		return "login";
	}
	
	@GetMapping("/task/{userId}")
	public String task(Model model, HttpServletRequest request, @PathVariable("userId") String userId) throws SQLException {
		String sid = (String) request.getSession().getAttribute("sid");
		if(!Constants.loginCache.containsKey(sid)) {
			//已经登录，重定向到首页
			return "redirect:/login";
		}else{
			JSONObject record = mainService.getByUserId(userId);
			if(record.isEmpty()) {
				return "redirect:/login";
			}else {
				model.addAttribute("record", record);
			}
		}
		return "task";
	}

}
