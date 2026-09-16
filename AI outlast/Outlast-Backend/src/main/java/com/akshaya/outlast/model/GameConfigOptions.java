package com.akshaya.outlast.model;

import com.akshaya.outlast.utils.Gender;
import java.util.List;

public class GameConfigOptions {
    private boolean useDefaults = true;
    private int numberOfTeams = 2;
    private int numberOfPlayers = 6;
    private List<PlayerDetails> customPlayers;
    private boolean stepByStep = false;

    public void validate() {
        if (numberOfTeams < 2 || numberOfTeams > 4) {
            throw new IllegalArgumentException("Choose between 2 and 4 tribes.");
        }
        if (numberOfPlayers < numberOfTeams * 2 || numberOfPlayers > 16 || numberOfPlayers % numberOfTeams != 0) {
            throw new IllegalArgumentException("Choose 2 or more contestants per tribe, evenly divided, up to 16 total.");
        }
        if (!useDefaults) {
            if (customPlayers == null || customPlayers.size() != numberOfPlayers) {
                throw new IllegalArgumentException("The contestant list must match the selected player count.");
            }
            java.util.Set<String> names = new java.util.HashSet<>();
            for (PlayerDetails player : customPlayers) {
                if (player == null || player.name == null || player.name.isBlank() || player.name.trim().length() > 40 || player.gender == null) {
                    throw new IllegalArgumentException("Every contestant needs a name of 1–40 characters and a gender.");
                }
                player.name = player.name.trim();
                if (!names.add(player.name.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("Each contestant needs a different name.");
                }
            }
        }
    }

    public boolean isUseDefaults() {
        return useDefaults;
    }

    public void setUseDefaults(boolean useDefaults) {
        this.useDefaults = useDefaults;
    }

    public int getNumberOfTeams() {
        return numberOfTeams;
    }

    public void setNumberOfTeams(int numberOfTeams) {
        this.numberOfTeams = numberOfTeams;
    }

    public int getNumberOfPlayers() {
        return numberOfPlayers;
    }

    public void setNumberOfPlayers(int numberOfPlayers) {
        this.numberOfPlayers = numberOfPlayers;
    }

    public List<PlayerDetails> getCustomPlayers() {
        return customPlayers;
    }

    public void setCustomPlayers(List<PlayerDetails> customPlayers) {
        this.customPlayers = customPlayers;
    }

    public boolean isStepByStep() {
        return stepByStep;
    }

    public void setStepByStep(boolean stepByStep) {
        this.stepByStep = stepByStep;
    }

    public static class PlayerDetails {
        private String name;
        private Gender gender;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Gender getGender() {
            return gender;
        }

        public void setGender(Gender gender) {
            this.gender = gender;
        }
    }
}
