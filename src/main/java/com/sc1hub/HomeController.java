package com.sc1hub;

import com.sc1hub.common.dto.PageDTO;
import com.sc1hub.visitorCount.VisitorCountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class HomeController {

    @Autowired
    VisitorCountService visitorCountService;

    @GetMapping("/")
    public String home(PageDTO page, Model model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        visitorCountService.processVisitor(request, response);

        model.addAttribute("todayCount", visitorCountService.getTodayCount());
        model.addAttribute("totalCount", visitorCountService.getTotalCount());

        return "index";
    }

    @GetMapping("/guidelines")
    public String showGuidelines() {
        return "guidelines";
    }

}