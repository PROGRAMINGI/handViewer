package com.example.handViewer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HandController {

    private final HandParser handParser;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "포커 핸드 히스토리 뷰어");
        return "home";
    }

    @PostMapping("/parse")
    public String parseHistory(
            @RequestParam("historyText") String historyText,
            @RequestParam(value = "isAnonymous", defaultValue = "false") boolean isAnonymous,
            @RequestParam(value = "isBBMode", defaultValue = "false") boolean isBBMode,
            Model model) {

        HandParser.HandResult result = handParser.parse(historyText, isAnonymous, isBBMode);

        model.addAttribute("title", "분석 결과");
        model.addAttribute("result", result);
        model.addAttribute("originalText", historyText);

        return "home";
    }
}