package at.berger.timesup.entity;

import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.GameMessage;
import lombok.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GameEntity {
    private String id;
    private String name;
    private GameState state;
    private GamePhase phase;

    private String description;
    private Integer entriesPerPlayer;
    private Integer roundTime;
    private boolean assignTeams;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private List<GameRoundEntity> rounds = new ArrayList<>();

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private SortedMap<String, GameTeamEntity> teams = new TreeMap<>();

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Set<String> allEntries = new HashSet<>();

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Set<String> availableEntries = new HashSet<>();

    public List<GameTeamEntity> getTeams() {
        return new ArrayList<>(teams.values());
    }

    public boolean containsTeam(String teamName) {
        return teams.containsKey(teamName);
    }

    public void addEntries(Set<String> entries) {
        allEntries.addAll(entries);
        availableEntries.addAll(entries);
    }

    public GameRoundEntity getCurrentRound() {
        if (rounds.isEmpty()) {
            return null;
        }
        return rounds.get(rounds.size() - 1);
    }

    public void addTeams(List<GameTeamEntity> teams) {
        Set<GameTeamEntity> teamsSet = new HashSet<>(teams);
        if(teamsSet.size() < teams.size()){
            throw new GameException(new GameMessage("Team names have to be unique"));
        }
        boolean containTeam = teams.stream().anyMatch(t -> this.teams.containsKey(t.getName()));
        if (containTeam) {
            throw new GameException(new GameMessage("Team names have to be unique"));
        }
        this.teams.putAll(teams.stream()
                .collect(Collectors.toMap(GameTeamEntity::getName, Function.identity())));
    }

    public void addPlayerEntries(String teamName, String playerName, Set<String> entries) {
        addPlayer(teamName, playerName);
        addEntries(entries);
    }

    public void addPlayer(String teamName, String playerName) {
        getNonNullTeam(teamName).addPlayer(playerName);
    }

    public GameTeamEntity getRandomTeam() {
        Random generator = new Random();
        GameTeamEntity[] values = teams.values().toArray(new GameTeamEntity[0]);
        return values[generator.nextInt(values.length)];
    }

    public void addRound(GameRoundEntity newRound) {
        rounds.add(newRound);
    }

    public List<String> getAvailableEntries() {
        return new ArrayList<>(availableEntries);
    }

    public void removeEntries(List<String> correctEntries) {
        availableEntries.removeAll(correctEntries);
    }

    public GameTeamEntity getNextTeam(String team) {
        GameTeamEntity currentTeam = getNonNullTeam(team);
        List<GameTeamEntity> teamList = new ArrayList<>(teams.values());
        int teamIndex = teamList.indexOf(currentTeam);
        if (teamIndex + 1 == teamList.size()) {
            return teamList.get(0);
        }
        return teamList.get(teamIndex + 1);
    }

    private GameTeamEntity getNonNullTeam(String teamName) {
        GameTeamEntity team = teams.get(teamName);
        if (team == null) {
            throw new GameException(new GameMessage("Team " + teamName + " does not exists"));
        }
        return team;
    }

    public GameRoundEntity getLastRound(String teamName) {
        return rounds.stream()
                .filter(r -> r.getTeamName().equals(teamName)).reduce((r1, r2) -> r2)
                .orElse(null);
    }

    public boolean hasMoreEntries() {
        return !availableEntries.isEmpty();
    }

    public void resetEntries() {
        availableEntries = new HashSet<>(allEntries);
    }

    public void removePlayer(String playerName, boolean force) {
        GameTeamEntity team = getPlayerTeam(playerName);
        if (!force && team.getPlayers().size() <= 1) {
            throw new GameException(new GameMessage("Cannot remove player of team that has one or less players"));
        }
        team.removePlayer(playerName);
    }

    public GameTeamEntity getPlayerTeam(String playerName) {
        return teams.values().stream()
                .filter(t -> t.getPlayers().contains(playerName))
                .findAny()
                .orElseThrow(() -> new GameException(new GameMessage(String.format("Player %s is not in any team", playerName))));
    }

    public List<TeamScore> getTeamScores() {
        Map<String, List<GameRoundEntity>> roundsByTeam = rounds.stream()
                .collect(Collectors.groupingBy(GameRoundEntity::getTeamName));

        return teams.values().stream()
                .map(team -> {
                    List<GameRoundEntity> teamRounds = roundsByTeam.get(team.getName());
                    if (teamRounds != null) {
                        int teamScore = teamRounds.stream()
                                .map(r -> r.getCorrectEntries().size())
                                .reduce(Integer::sum)
                                .orElse(0);
                        Map<String, List<GameRoundEntity>> teamRoundsByPlayer = teamRounds.stream()
                                .collect(Collectors.groupingBy(GameRoundEntity::getPlayerName));
                        Collection<String> allPlayers = new TreeSet<>(team.getPlayers());
                        allPlayers.addAll(teamRoundsByPlayer.keySet());
                        List<PlayerScore> playerScores = allPlayers.stream()
                                .map(player -> {
                                    List<GameRoundEntity> playerRounds = teamRoundsByPlayer.get(player);
                                    if (playerRounds != null) {
                                        Integer playerScore = playerRounds.stream()
                                                .map(r -> r.getCorrectEntries().size())
                                                .reduce(Integer::sum)
                                                .orElse(0);
                                        return new PlayerScore(player, playerScore, !team.getPlayers().contains(player));
                                    } else {
                                        return new PlayerScore(player, 0, false);
                                    }
                                }).collect(Collectors.toList());
                        return new TeamScore(team.getName(), teamScore, playerScores);
                    } else {
                        List<PlayerScore> playerScores = team.getPlayers().stream()
                                .map(p -> new PlayerScore(p, 0, false))
                                .collect(Collectors.toList());
                        return new TeamScore(team.getName(), 0, playerScores);
                    }
                }).collect(Collectors.toList());
    }

    public int getPlayerCount() {
        return getTeams().stream()
                .map(t -> t.getPlayers().size())
                .reduce(Integer::sum)
                .orElse(0);
    }

    public List<String> getTotalEntries() {
        return new ArrayList<>(allEntries);
    }

    public GameTeamEntity getTeam(String teamName) {
        GameTeamEntity team = teams.get(teamName);
        if (team == null) {
            throw new GameException(new GameMessage("Cannot find team " + teamName));
        }
        return team;
    }

    public GameTeamEntity getRandomTeamWithLeastPlayers() {
        return teams.values().stream()
                .reduce((t1, t2) -> {
                    if (t1.getPlayers().size() > t2.getPlayers().size()) {
                        return t2;
                    }
                    if (t2.getPlayers().size() > t1.getPlayers().size()) {
                        return t1;
                    }
                    Random generator = new Random();
                    GameTeamEntity[] values = new GameTeamEntity[] { t1, t2 };
                    return values[generator.nextInt(values.length)];
                }).orElseThrow(() -> new GameException(new GameMessage("No teams found")));
    }

    public GameTeamEntity getRandomTeamWithMostPlayers() {
        return teams.values().stream()
                .reduce((t1, t2) -> {
                    if (t1.getPlayers().size() > t2.getPlayers().size()) {
                        return t1;
                    }
                    if (t2.getPlayers().size() > t1.getPlayers().size()) {
                        return t2;
                    }
                    Random generator = new Random();
                    GameTeamEntity[] values = new GameTeamEntity[] { t1, t2 };
                    return values[generator.nextInt(values.length)];
                }).orElseThrow(() -> new GameException(new GameMessage("No teams found")));
    }


    @Data
    @AllArgsConstructor
    public static class TeamScore {
        private String team;
        private Integer score;
        private List<PlayerScore> players = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    public static class PlayerScore {
        private String player;
        private Integer score;
        private boolean synthetic;
    }
}
