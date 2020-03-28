package at.berger.timesup.controller;

import at.berger.timesup.entity.GameEntity;
import at.berger.timesup.entity.GameState;
import at.berger.timesup.model.GameScreenModel;
import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.GameMessage;
import at.berger.timesup.model.message.Severity;
import at.berger.timesup.service.GameService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;

@Controller
@AllArgsConstructor
public class GameController {

    private final GameService gameService;

    private final SessionModel session;

    @GetMapping("/start")
    public String getStart(Model model) {
        if (session.getGameId() != null) {
            if (session.getPlayerName() != null) {
                return "redirect:play";
            }
            return "redirect:joinSetupGame";
        }
        model.addAttribute("games", gameService.getAvailableGames());
        return "start";
    }

    @GetMapping("/")
    public String getAbout() {
        return "about";
    }

    @GetMapping("/createGame")
    public String createGame(@RequestParam String gameName,
                             @RequestParam List<String> teams,
                             @RequestParam(required = false) String description,
                             @RequestParam Integer entriesPerPlayer,
                             @RequestParam Integer roundTime,
                             @RequestParam boolean assignTeams) {
        if (session.getGameId() != null) {
            if (session.getPlayerName() != null) {
                return "redirect:play";
            }
            return "redirect:joinSetupGame";
        }
        GameEntity game = gameService.createGame(gameName, teams, roundTime * 1000, entriesPerPlayer, description, assignTeams);
        session.setGameId(game.getId());
        session.setAdmin(true);
        return "redirect:joinSetupGame";
    }

    @GetMapping("/joinGame")
    public String joinGame(@RequestParam String gameId) {
        if (session.getGameId() != null) {
            if (session.getPlayerName() != null) {
                return "redirect:play";
            }
            return "redirect:joinSetupGame";
        }
        gameService.validateCanJoin(gameId);
        session.setGameId(gameId);
        session.setAdmin(false);
        GameEntity game = gameService.getGame(gameId);
        if (GameState.SETUP.equals(game.getState())) {
            return "redirect:joinSetupGame";
        }
        return "redirect:joinPlayGame";
    }

    @GetMapping("/joinSetupGame")
    public String getJoinSetupGame(Model model) {
        if (session.getGameId() == null) {
            return "redirect:start";
        }
        if (session.getPlayerName() != null) {
            return "redirect:play";
        }

        model.addAttribute("session", session);
        model.addAttribute("game", gameService.getGame(session.getGameId()));
        model.addAttribute("refresh", true);
        return "joinSetupGame";
    }

    @GetMapping("/joinPlayGame")
    public String getJoinPlayGame(Model model) {
        if (session.getGameId() == null) {
            return "redirect:start";
        }
        if (session.getPlayerName() != null) {
            return "redirect:play";
        }

        model.addAttribute("session", session);
        model.addAttribute("game", gameService.getGame(session.getGameId()));
        model.addAttribute("refresh", true);
        return "joinPlayGame";
    }


    @GetMapping("/doJoinSetupGame")
    public String addGameEntries(@RequestParam String playerName,
                                 @RequestParam(required = false) String teamName,
                                 @RequestParam List<String> gameEntries) {
        if (session.getGameId() == null) {
            return "redirect:start";
        }
        if (session.getPlayerName() != null) {
            return "redirect:play";
        }

        teamName = gameService.joinSetupGame(session.getGameId(), teamName, playerName, new HashSet<>(gameEntries));
        session.setPlayerName(playerName);
        session.setTeamName(teamName);
        return "redirect:play";
    }

    @GetMapping("/doJoinPlayGame")
    public String addGameEntries(@RequestParam String playerName,
                                 @RequestParam String teamName) {
        if (session.getGameId() == null) {
            return "redirect:start";
        }
        if (session.getPlayerName() != null) {
            return "redirect:play";
        }

        gameService.joinPlayGame(session.getGameId(), teamName, playerName);
        session.setPlayerName(playerName);
        session.setTeamName(teamName);
        session.setAdmin(false);
        return "redirect:play";
    }

    @GetMapping("/play")
    public String getScreen(Model model) {
        if (session.getGameId() == null || session.getPlayerName() == null) {
            return "redirect:start";
        }
        GameScreenModel gameScreen = gameService.getGameScreen(session.getGameId(), session.getTeamName(), session.getPlayerName());
        GameEntity game = gameScreen.getGame();
        switch (game.getState()) {
            case SETUP:
                model.addAttribute("refresh", true);
                break;
            case END:
                model.addAttribute("refresh", false);
                break;
            case PLAY: {
                if (game.getCurrentRound().getPlayerName().equals(session.getPlayerName())) {
                    model.addAttribute("refresh", false);
                } else {
                    model.addAttribute("refresh", true);
                }
                break;
            }
        }

        return "play";
    }

    @GetMapping("/correctEntry")
    public String correctEntry(@RequestParam String entry) {
        if (session.getGameId() == null || session.getPlayerName() == null) {
            return "redirect:start";
        }
        gameService.correctEntry(session.getGameId(), session.getPlayerName(), entry);
        return "redirect:play";
    }

    @GetMapping("/kickPlayer")
    public String kickPlayer(@RequestParam String playerName) {
        if (session.getGameId() == null || session.getPlayerName() == null) {
            return "redirect:start";
        }
        if (!session.isAdmin()) {
            throw new GameException(Severity.ERROR, new GameMessage("Only admin can kick player"));
        }
        if (session.getPlayerName().equals(playerName)) {
            throw new GameException(Severity.ERROR, new GameMessage("Cannot kick self"));
        }
        gameService.kickPlayer(session.getGameId(), playerName);
        return "redirect:play";
    }

    @GetMapping("/rejectEntry")
    public String rejectEntry(@RequestParam String entry, Model model) {
        if (session.getGameId() == null || session.getPlayerName() == null) {
            return "redirect:start";
        }
        gameService.rejectEntry(session.getGameId(), session.getPlayerName(), entry);
        return "redirect:play";
    }

    @GetMapping("/startGame")
    public String startGame(Model model) {
        if (session.getGameId() == null || session.getPlayerName() == null) {
            return "redirect:start";
        }
        if (session.isAdmin()) {
            gameService.startGame(session.getGameId());
        }
        return "redirect:play";
    }

    @GetMapping("/startRound")
    public String startRound(Model model) {
        if (session.getGameId() == null || session.getPlayerName() == null) {
            return "redirect:start";
        }
        gameService.startRound(session.getGameId(), session.getPlayerName());
        return "redirect:play";
    }

    @GetMapping("/leaveGame")
    public String leaveGame() {
        if (session.getGameId() == null) {
            return "redirect:start";
        }
        gameService.leaveGame(session.getGameId(), session.getPlayerName(), session.isAdmin());
        session.setGameId(null);
        session.setAdmin(false);
        session.setTeamName(null);
        session.setPlayerName(null);
        return "redirect:start";
    }

    @ExceptionHandler(GameException.class)
    public String handleError(HttpServletRequest req, GameException ex, RedirectAttributes attributes) {
        attributes.addAttribute("error", ex.getMessage());
        if (Severity.FATAL.equals(ex.getSeverity())) {
            session.setGameId(null);
            session.setAdmin(false);
            session.setTeamName(null);
            session.setPlayerName(null);
        }
        if (session.getGameId() != null) {
            if (session.getPlayerName() != null) {
                return "redirect:play";
            }
            return "redirect:joinSetupGame";
        }
        return "redirect:play";
    }
}