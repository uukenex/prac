package my.prac.api.wedding.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/wedding")
public class WeddingController {

    @GetMapping("/propose")
    public String propose() {
        return "nonsession/wedding/propose";
    }

    @GetMapping("/invite")
    public String invite() {
        return "nonsession/wedding/invite";
    }
}
