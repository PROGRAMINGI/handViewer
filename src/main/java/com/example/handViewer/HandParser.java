package com.example.handViewer;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HandParser {

    @Data
    public static class Player {
        private String name;
        private int seatNumber;
        private boolean isButton;
        private boolean isHero;
        private boolean isWinner;
        private String position;
        private List<Card> cards = new ArrayList<>();

        private double stack;
        private String stackDisplay;

        public Player(String name, int seatNumber, double stack) {
            this.name = name;
            this.seatNumber = seatNumber;
            this.stack = stack;
            this.stackDisplay = String.format("$%.2f", stack);
        }
    }

    @Data
    @NoArgsConstructor
    public static class HandResult {
        private List<Player> players = new ArrayList<>();
        private List<Card> boardCards = new ArrayList<>();
        private List<String> preflopActions = new ArrayList<>();
        private List<String> flopActions = new ArrayList<>();
        private List<String> turnActions = new ArrayList<>();
        private List<String> riverActions = new ArrayList<>();
        private String potPreflop = "$0.00";
        private String potFlop = "$0.00";
        private String potTurn = "$0.00";
        private String potRiver = "$0.00";
        private List<Card> opponentCards = new ArrayList<>();
        private List<Card> heroCards = new ArrayList<>();
        private String heroName;
    }

    public HandResult parse(String historyText, boolean isAnonymous, boolean isBBMode) {
        HandResult result = new HandResult();
        String[] lines = historyText.split("\n");

        String currentStage = "PREFLOP";
        int buttonSeat = 0;
        double totalPot = 0.0;
        double bbSize = 0.0;
        Map<String, Double> streetCommitments = new HashMap<>();

        for (String line : lines) {
            line = line.trim();

            if (bbSize == 0.0 && line.contains("($") && line.contains("/")) {
                bbSize = extractBigBlindSize(line);
            }
            if (line.contains("is the button")) {
                try {
                    String num = line.substring(line.indexOf("#") + 1, line.indexOf(" is"));
                    buttonSeat = Integer.parseInt(num);
                } catch(Exception e) {}
            }

            if (line.startsWith("Seat ") && line.contains("in chips")) {
                try {
                    int seatNum = Integer.parseInt(line.substring(5, line.indexOf(":")));
                    String namePart = line.substring(line.indexOf(":") + 1, line.indexOf("(")).trim();

                    double stackSize = extractMoney(line.substring(line.indexOf("(")));

                    Player player = new Player(namePart, seatNum, stackSize);
                    if (seatNum == buttonSeat) player.setButton(true);
                    result.getPlayers().add(player);
                } catch (Exception e) {}
            }
        }

        if (bbSize == 0.0) bbSize = 1.0;
        calculatePositions(result);

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.contains("*** HOLE CARDS ***")) {
                result.setPotPreflop(String.format("$%.2f", totalPot));
            } else if (line.contains("*** FLOP ***")) {
                currentStage = "FLOP";
                result.setPotFlop(String.format("$%.2f", totalPot));
                streetCommitments.clear();
                if (line.contains("[")) result.getBoardCards().addAll(extractCards(line));
            } else if (line.contains("*** TURN ***")) {
                currentStage = "TURN";
                result.setPotTurn(String.format("$%.2f", totalPot));
                streetCommitments.clear();
                if (line.contains("[")) addNewCard(result.getBoardCards(), line);
            } else if (line.contains("*** RIVER ***")) {
                currentStage = "RIVER";
                result.setPotRiver(String.format("$%.2f", totalPot));
                streetCommitments.clear();
                if (line.contains("[")) addNewCard(result.getBoardCards(), line);
            }

            if (line.startsWith("Dealt to")) {
                String extractedName = line.substring(9, line.lastIndexOf("[")).trim();
                result.setHeroName(extractedName);
                List<Card> heroCards = extractCards(line);
                result.setHeroCards(heroCards);
                for (Player p : result.getPlayers()) {
                    if (p.getName().equals(extractedName)) {
                        p.setHero(true);
                        p.setCards(heroCards);
                    }
                }
            } else if (line.contains("shows [") || line.contains("showed [")) {
                String playerName = line.substring(0, line.indexOf(":")).trim();
                List<Card> shownCards = extractCards(line);
                boolean isHeroLine = false;
                for (Player p : result.getPlayers()) {
                    if (p.getName().equals(playerName)) { p.setCards(shownCards); if (p.isHero()) isHeroLine = true; }
                }
                if (!isHeroLine) result.setOpponentCards(shownCards);
            }

            String playerName = "";
            if (line.contains(":")) playerName = line.substring(0, line.indexOf(":"));

            if (line.contains("posts") && line.contains("$")) {
                double amount = extractMoney(line);
                totalPot += amount;
                streetCommitments.put(playerName, streetCommitments.getOrDefault(playerName, 0.0) + amount);
            } else if ((line.contains("bets") || line.contains("calls")) && line.contains("$")) {
                double amount = extractMoney(line);
                totalPot += amount;
                streetCommitments.put(playerName, streetCommitments.getOrDefault(playerName, 0.0) + amount);
            } else if (line.contains("raises") && line.contains("to $")) {
                double totalCommit = extractMoney(line);
                double alreadyCommitted = streetCommitments.getOrDefault(playerName, 0.0);
                double amountAdded = totalCommit - alreadyCommitted;
                if (amountAdded > 0) totalPot += amountAdded;
                streetCommitments.put(playerName, totalCommit);
            } else if (line.contains("Uncalled bet") && line.contains("returned to")) {
                double amount = extractMoney(line);
                totalPot -= amount;
            }

            if (line.contains(" collected ") && line.contains(" from pot")) {
                String winnerName = line.substring(0, line.indexOf(" collected")).trim();
                for (Player p : result.getPlayers()) {
                    if (p.getName().equals(winnerName)) p.setWinner(true);
                }
            }

            if (line.contains(":") && isActionLine(line)) {
                switch (currentStage) {
                    case "PREFLOP" -> result.getPreflopActions().add(line);
                    case "FLOP" -> result.getFlopActions().add(line);
                    case "TURN" -> result.getTurnActions().add(line);
                    case "RIVER" -> result.getRiverActions().add(line);
                }
            }
        }

        if (isBBMode) {
            applyBBConversion(result, bbSize);
        }
        if (isAnonymous) {
            applyAnonymization(result);
            for (Player p : result.getPlayers()) {
                if (p.isHero()) { result.setHeroName(p.getName()); break; }
            }
        }

        return result;
    }

    private void applyBBConversion(HandResult result, double bbSize) {
        result.setPotPreflop(convertMoneyToBB(result.getPotPreflop(), bbSize));
        result.setPotFlop(convertMoneyToBB(result.getPotFlop(), bbSize));
        result.setPotTurn(convertMoneyToBB(result.getPotTurn(), bbSize));
        result.setPotRiver(convertMoneyToBB(result.getPotRiver(), bbSize));

        convertLogsToBB(result.getPreflopActions(), bbSize);
        convertLogsToBB(result.getFlopActions(), bbSize);
        convertLogsToBB(result.getTurnActions(), bbSize);
        convertLogsToBB(result.getRiverActions(), bbSize);

        for (Player p : result.getPlayers()) {
            double bbStack = p.getStack() / bbSize;
            p.setStackDisplay(String.format("%.1f BB", bbStack));
        }
    }

    private void convertLogsToBB(List<String> logs, double bbSize) {
        for (int i = 0; i < logs.size(); i++) {
            logs.set(i, convertMoneyToBB(logs.get(i), bbSize));
        }
    }

    private String convertMoneyToBB(String text, double bbSize) {
        if (text == null) return "";
        Pattern p = Pattern.compile("\\$(\\d+(\\.\\d+)?)");
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            double amount = Double.parseDouble(m.group(1));
            double bbAmount = amount / bbSize;
            String replacement = String.format("%.1f BB", bbAmount);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private double extractBigBlindSize(String line) {
        try {
            Pattern p = Pattern.compile("\\/\\$(\\d+(\\.\\d+)?)");
            Matcher m = p.matcher(line);
            if (m.find()) return Double.parseDouble(m.group(1));
        } catch (Exception e) {}
        return 0.0;
    }

    private void calculatePositions(HandResult result) {
        List<Player> players = result.getPlayers();
        if (players.isEmpty()) return;
        players.sort(Comparator.comparingInt(Player::getSeatNumber));
        int btnIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).isButton()) { btnIndex = i; break; }
        }
        if (btnIndex == -1) btnIndex = 0;
        int numPlayers = players.size();
        for (int i = 0; i < numPlayers; i++) {
            int offset = (i - (btnIndex + 1) + numPlayers) % numPlayers;
            Player p = players.get(i);
            p.setPosition(getPositionName(numPlayers, offset));
        }
    }

    private String getPositionName(int numPlayers, int offset) {
        if (numPlayers == 2) return (offset == 0) ? "BB" : "BTN(SB)";
        if (offset == 0) return "SB";
        if (offset == 1) return "BB";
        if (offset == numPlayers - 1) return "BTN";
        if (offset == numPlayers - 2) return "CO";
        if (numPlayers >= 5 && offset == 2) return "UTG";
        if (numPlayers == 6 && offset == 3) return "MP";
        return "Pos" + (offset + 1);
    }

    private void applyAnonymization(HandResult result) {
        Map<String, String> nameMap = new HashMap<>();
        for (Player p : result.getPlayers()) {
            nameMap.put(p.getName(), p.getPosition());
            p.setName(p.getPosition());
        }
        result.setPreflopActions(replaceNamesInLogs(result.getPreflopActions(), nameMap));
        result.setFlopActions(replaceNamesInLogs(result.getFlopActions(), nameMap));
        result.setTurnActions(replaceNamesInLogs(result.getTurnActions(), nameMap));
        result.setRiverActions(replaceNamesInLogs(result.getRiverActions(), nameMap));
    }

    private List<String> replaceNamesInLogs(List<String> logs, Map<String, String> nameMap) {
        List<String> newLogs = new ArrayList<>();
        for (String log : logs) {
            String tempLog = log;
            for (Map.Entry<String, String> entry : nameMap.entrySet()) {
                tempLog = tempLog.replace(entry.getKey(), entry.getValue());
            }
            newLogs.add(tempLog);
        }
        return newLogs;
    }

    private boolean isActionLine(String line) {
        return line.contains("folds") || line.contains("checks") || line.contains("calls") ||
                line.contains("bets") || line.contains("raises") || line.contains("posts");
    }

    private double extractMoney(String line) {
        try {
            Pattern p = Pattern.compile("\\$(\\d+(\\.\\d+)?)");
            Matcher m = p.matcher(line);
            double val = 0.0;
            while (m.find()) {
                val = Double.parseDouble(m.group(1));
            }
            return val;
        } catch (Exception e) { return 0.0; }
    }

    private List<Card> extractCards(String line) {
        List<Card> cards = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[(.*?)\\]");
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String content = matcher.group(1);
            String[] parts = content.split(" ");
            for (String part : parts) {
                if (!part.trim().isEmpty()) cards.add(convertStringToCard(part));
            }
        }
        return cards;
    }

    private void addNewCard(List<Card> board, String line) {
        List<Card> cards = extractCards(line);
        if (!cards.isEmpty()) board.add(cards.get(cards.size() - 1));
    }

    private Card convertStringToCard(String text) {
        if (text == null || text.length() < 2) return null;
        String rankChar = text.substring(0, text.length() - 1);
        String suitChar = text.substring(text.length() - 1);
        String rank = rankChar.replace("T", "10");
        String suit = switch (suitChar) {
            case "h" -> "♥"; case "d" -> "♦"; case "c" -> "♣"; case "s" -> "♠"; default -> "?";
        };
        return new Card(suit, rank);
    }
}