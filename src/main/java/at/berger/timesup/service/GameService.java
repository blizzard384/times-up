package at.berger.timesup.service;

import at.berger.timesup.entity.*;
import at.berger.timesup.model.GameScreenModel;
import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.GameMessage;
import at.berger.timesup.model.message.Severity;
import at.berger.timesup.repository.GameRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@AllArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    public GameEntity createGame(String gameName, List<String> teams, Integer roundTime, Integer entriesPerPlayer, String description, boolean assignTeams) {
        requireNotEmptyString(gameName);
        requireNotEmptyString(teams.toArray(new String[0]));
        GameEntity gameEntity = new GameEntity();
        gameEntity.setId(UUID.randomUUID().toString());
        gameEntity.setName(gameName);
        gameEntity.addTeams(teams.stream().map(GameTeamEntity::new).collect(Collectors.toList()));
        gameEntity.setState(GameState.SETUP);
        gameEntity.setRoundTime(roundTime);
        gameEntity.setEntriesPerPlayer(entriesPerPlayer);
        gameEntity.setDescription(description);
        gameEntity.setAssignTeams(assignTeams);
        gameRepository.create(gameEntity);

        gameRepository.removeIn(gameEntity.getId(), 1000 * 60 * 60 * 4);
        return gameEntity;
    }

    public String joinSetupGame(String gameId, String teamName, String playerName, Set<String> entries) {
        requireNotEmptyString(gameId, playerName);
        requireNotEmptyString(entries.toArray(new String[0]));
        requireState(gameId, GameState.SETUP);
        return gameRepository.update(gameId, game -> {
            if (game.getEntriesPerPlayer() != entries.size()) {
                throw new GameException(new GameMessage("Must provide " + game.getEntriesPerPlayer() + " unique entries."));
            }
            if (game.isAssignTeams()) {
                GameTeamEntity assignedTeam = game.getRandomTeamWithLeastPlayers();
                game.addPlayerEntries(assignedTeam.getName(), playerName, entries);
                return assignedTeam.getName();
            } else {
                requireNotEmptyString(teamName);
                requireTeam(gameId, teamName);
                game.addPlayerEntries(teamName, playerName, entries);
                return teamName;
            }
        });
    }

    public void joinPlayGame(String gameId, String teamName, String playerName) {
        requireNotEmptyString(gameId, teamName, playerName);
        requireState(gameId, GameState.PLAY);
        gameRepository.update(gameId, game -> {
            requireTeam(gameId, teamName);
            game.addPlayer(teamName, playerName);
        });
    }

    public GameScreenModel getGameScreen(String gameId, String teamName, String playerName) {
        requirePlayer(gameId, teamName, playerName);
        return new GameScreenModel(gameRepository.get(gameId));
    }

    public GameEntity getGame(String gameId) {
        return gameRepository.get(gameId);
    }

    public void correctEntry(String gameId, String playerName, String entry) {
        requireState(gameId, GameState.PLAY);
        requireRound(gameId, GameRoundState.IN_PROGRESS, playerName);
        gameRepository.update(gameId, game -> {
            GameRoundEntity currentRound = game.getCurrentRound();
            if (!currentRound.addCorrectEntry(entry)) {
                if (setupNewPhase(game)) {
                    Integer remainingTime = currentRound.getRemainingTime();
                    GameTeamEntity team = game.getTeam(currentRound.getTeamName());
                    setupNewRound(game, team, currentRound.getPlayerName(), remainingTime);
                }
            }
        });
    }

    public void rejectEntry(String gameId, String playerName, String entry) {
        requireState(gameId, GameState.PLAY);
        requireRound(gameId, GameRoundState.IN_PROGRESS, playerName);
        gameRepository.update(gameId, game -> {
            GameRoundEntity currentRound = game.getCurrentRound();
            currentRound.rejectEntry(entry);
        });
    }

    public void startRound(String gameId, String playerName) {
        requireState(gameId, GameState.PLAY);
        requireRound(gameId, GameRoundState.AWAIT_START, playerName);
        String currentRoundId = gameRepository.get(gameId).getCurrentRound().getId();
        gameRepository.update(gameId, game -> {
            game.getCurrentRound().setState(GameRoundState.IN_PROGRESS);
            game.getCurrentRound().setInProgressTimestamp(Instant.now());
        });
        Integer roundTime = gameRepository.get(gameId).getCurrentRound().getRoundTime();
        gameRepository.updateIn(gameId, roundTime, game -> {
            if (!game.getCurrentRound().getId().equals(currentRoundId)) {
                return;
            }
            requireState(gameId, GameState.PLAY);
            requireRound(gameId, GameRoundState.IN_PROGRESS, playerName);
            GameRoundEntity currentRound = game.getCurrentRound();
            currentRound.setState(GameRoundState.END);
            game.removeEntries(currentRound.getCorrectEntries());

            if (game.hasMoreEntries()) {
                setupNextRound(game);
            } else {
                if (setupNewPhase(game)) {
                    setupNextRound(game);
                }
            }
        });
    }

    private boolean setupNewPhase(GameEntity game) {
        List<GamePhase> phases = Arrays.asList(GamePhase.values());
        int indexOfPhase = phases.indexOf(game.getPhase());
        if (indexOfPhase + 1 == phases.size()) {
            game.setPhase(null);
            game.setState(GameState.END);
            return false;
        } else {
            game.setPhase(phases.get(indexOfPhase + 1));
            game.resetEntries();
            return true;
        }
    }

    private void setupNextRound(GameEntity game) {
        GameTeamEntity nextTeam = game.getNextTeam(game.getCurrentRound().getTeamName());
        GameRoundEntity previousRound = game.getLastRound(nextTeam.getName());
        if (previousRound != null) {
            setupNewRound(game, nextTeam, nextTeam.getNextPlayer(previousRound.getPlayerName()), game.getRoundTime());
        } else {
            setupNewRound(game, nextTeam, nextTeam.getRandomPlayer(), game.getRoundTime());
        }
    }

    public void startGame(String gameId) {
        requireState(gameId, GameState.SETUP);
        gameRepository.update(gameId, game -> {
            if (!game.getTeams().stream().allMatch(t -> t.getPlayers().size() >= 2)) {
                throw new GameException(new GameMessage("Each team must have atleast two players to start game"));
            }
            game.setState(GameState.PLAY);
            game.setPhase(GamePhase.ALL_WORDS);
            GameTeamEntity team = game.getRandomTeam();
            setupNewRound(game, team, team.getRandomPlayer(), game.getRoundTime());
        });
    }

    public List<GameEntity> getAvailableGames() {
        return gameRepository.getSetupAndPlay();
    }

    public void validateCanJoin(String gameId) {
        requireState(gameId, GameState.SETUP, GameState.PLAY);
    }

    public void leaveGame(String gameId, String playerName, boolean closeOnSetup) {
        GameEntity game = gameRepository.get(gameId);
        if (GameState.SETUP.equals(game.getState())) {
            if (closeOnSetup) {
                gameRepository.remove(gameId);
                return;
            }
            if (playerName != null) {
                gameRepository.update(gameId, g -> {
                    g.removePlayer(playerName, true);
                });
            }
            return;
        }
        GameTeamEntity team = game.getPlayerTeam(playerName);
        if (team.getPlayers().size() <= 1) {
            if (game.getTeams().stream().allMatch(t -> t.getPlayers().size() <= 1)) {
                gameRepository.remove(gameId);
                return;
            }
            adjustTeam(game, team);
        }
        removePlayer(gameId, playerName);
    }

    public void kickPlayer(String gameId, String playerName) {
        GameEntity game = gameRepository.get(gameId);
        GameTeamEntity team = game.getPlayerTeam(playerName);
        if (!GameState.SETUP.equals(game.getState())) {
            if (team.getPlayers().size() <= 1) {
                if (game.getTeams().stream().allMatch(t -> t.getPlayers().size() <= 1)) {
                    throw new GameException(new GameMessage("Cannot kick last team player"));
                }
                adjustTeam(game, team);
            }
        }
        removePlayer(gameId, playerName);
    }

    private void adjustTeam(GameEntity game, GameTeamEntity team) {
        GameTeamEntity cutTeam = game.getRandomTeamWithMostPlayers();
        String cutPlayer = cutTeam.getRandomPlayer();
        cutTeam.removePlayer(cutPlayer);
        team.addPlayer(cutPlayer);
    }

    private void removePlayer(String gameId, String playerName) {
        gameRepository.update(gameId, game -> {
            game.removePlayer(playerName, false);
            GameRoundEntity currentRound = game.getCurrentRound();
            if (currentRound != null
                    && currentRound.getPlayerName().equals(playerName)) {
                currentRound.setState(GameRoundState.END);
                Integer remainingTime = currentRound.getRemainingTime();
                GameTeamEntity team = game.getTeam(currentRound.getTeamName());
                setupNewRound(game, team, team.getRandomPlayer(), remainingTime);
            }
        });
    }


    private void setupNewRound(GameEntity game, GameTeamEntity team, String player, Integer roundTime) {
        GameRoundEntity newRound = new GameRoundEntity();
        newRound.setState(GameRoundState.AWAIT_START);
        newRound.setTeamName(team.getName());
        newRound.setPlayerName(player);
        newRound.addEntries(game.getAvailableEntries());
        newRound.setId(UUID.randomUUID().toString());
        newRound.setRoundTime(roundTime);
        newRound.setStartTimestamp(Instant.now());
        game.addRound(newRound);
    }

    private void requireRound(String gameId, GameRoundState roundState, String playerName) {
        GameRoundEntity round = gameRepository.get(gameId).getCurrentRound();
        if (!round.getPlayerName().equals(playerName)) {
            throw new GameException(new GameMessage("Round doesn't belong to player " + playerName));
        }
        if (!round.getState().equals(roundState)) {
            throw new GameException(new GameMessage("Round is not in state " + roundState));
        }
    }

    private void requireTeam(String gameId, String teamName) {
        if (!gameRepository.get(gameId).containsTeam(teamName)) {
            throw new GameException(new GameMessage("Game " + gameId + " does not contain team " + teamName));
        }
    }

    private void requireState(String gameId, GameState... states) {
        if (!Arrays.asList(states).contains(gameRepository.get(gameId).getState())) {
            throw new GameException(new GameMessage("Game needs to be in states " + states));
        }
    }

    private void requireNotEmptyString(String... strings) {
        if (Stream.of(strings).anyMatch(s -> s == null || s.isEmpty())) {
            throw new GameException(new GameMessage("Field cannot be empty"));
        }
    }

    private void requirePlayer(String gameId, String teamName, String playerName) {
        if (!getGame(gameId).getTeam(teamName).getPlayers().contains(playerName)) {
            throw new GameException(Severity.FATAL, new GameMessage("Game does not contain player"));
        }
    }
}
