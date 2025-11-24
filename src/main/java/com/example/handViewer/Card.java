package com.example.handViewer;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Card {
    private String suit;
    private String rank;

    public String getImageName() {
        String suitCode = "";

        switch (suit) {
            case "♠": suitCode = "S"; break;
            case "♥": suitCode = "H"; break;
            case "♦": suitCode = "D"; break;
            case "♣": suitCode = "C"; break;
            default: suitCode = "S";
        }

        return rank + suitCode + ".png";
    }
}