package com.akshaya.outlast.model;

import com.akshaya.outlast.utils.Gender;
import com.akshaya.outlast.model.Player;

import java.util.ArrayList;
import java.util.List;


public class Team {
    private static int counter = 0;
    private final String teamId;
    private final String teamName;
    private  Player captain;
    private final List<Player> players = new ArrayList<>();

    public Team(String teamName) {
        this.teamId = "team" + counter++;
        this.teamName = teamName;
    }

    public void setCaptain(Player captain) {
        this.captain = captain;
    }
    public Player getCaptain() {
        return captain;
    }
    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public String getTeamId() {
        return teamId;
    }
    public String getTeamName() {
        return teamName;
    }

    public List<Player> getPlayers() {
        return players;
    }

}