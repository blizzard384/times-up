package at.berger.timesup.entity;

import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.GameMessage;
import lombok.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameTeamEntity {
    private String name;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<String> players = new ArrayList<>();

    public GameTeamEntity(String name) {
        this.name = name;
    }

    public List<String> getPlayers() {
        return new ArrayList<>(players);
    }

    public void addPlayer(String playerName) {
        if (players.contains(playerName)) {
            throw new GameException(new GameMessage("Player with that name already exists in team " + name));
        }
        players.add(playerName);
    }

    public String getRandomPlayer() {
        Random rand = new Random();
        return players.get(rand.nextInt(players.size()));
    }

    public String getNextPlayer(String player) {
        if (!players.contains(player)) {
            throw new GameException(new GameMessage("Team " + name + " does not contain player " + player));
        }
        int teamIndex = players.indexOf(player);
        if (teamIndex + 1 == players.size()) {
            return players.get(0);
        }
        return players.get(teamIndex + 1);
    }

    public void removePlayer(String playerName) {
        players.remove(playerName);
    }
}
