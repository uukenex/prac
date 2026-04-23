package my.prac.api.wedding.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/bom")
public class WeddingController {

    @GetMapping("/invite")
    public String propose() {
        return "nonsession/wedding/propose";
    }

    @GetMapping("/link")
    public String invite() {
        return "nonsession/wedding/invite";
    }
}
