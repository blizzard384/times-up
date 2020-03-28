package at.berger.timesup.controller;

import at.berger.timesup.entity.GameEntity;
import at.berger.timesup.entity.GameRoundEntity;
import at.berger.timesup.entity.GameRoundState;
import at.berger.timesup.model.GameScreenModel;
import at.berger.timesup.model.message.GameException;
import at.berger.timesup.model.message.Severity;
import at.berger.timesup.service.GameService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

@Controller
@AllArgsConstructor
public class AjaxController {

    private final GameService gameService;

    private final SessionModel session;

    @GetMapping("/ajax/play")
    public String getScreen(Model model) {
        if (session.getGameId() == null) {
            return "ajax/noGame";
        }
        if (session.getPlayerName() == null) {
            model.addAttribute("player", session);
            model.addAttribute("game", gameService.getGame(session.getGameId()));
            return "ajax/roster";
        }
        GameScreenModel gameScreen = gameService.getGameScreen(session.getGameId(), session.getTeamName(), session.getPlayerName());
        GameEntity game = gameScreen.getGame();
        model.addAttribute("player", session);
        model.addAttribute("game", game);

        switch (game.getState()) {
            case SETUP:
                return "ajax/awaitSetup";
            case PLAY: {
                GameRoundEntity currentRound = game.getCurrentRound();
                if (!currentRound.getTeamName().equals(session.getTeamName())) {
                    if (GameRoundState.AWAIT_START.equals(currentRound.getState())) {
                        return "ajax/opponentTeamRoundAwait";
                    }
                    return "ajax/opponentTeamRoundInProgress";
                }
                if (!currentRound.getPlayerName().equals(session.getPlayerName())) {
                    if (GameRoundState.AWAIT_START.equals(currentRound.getState())) {
                        return "ajax/myTeamRoundAwait";
                    }
                    return "ajax/myTeamRoundInProgress";
                }
                if (GameRoundState.AWAIT_START.equals(currentRound.getState())) {
                    return "ajax/myRoundAwait";
                }
                return "ajax/myRoundInProgress";
            }
            case END:
                return "ajax/end";
        }

        return "ajax/noGame";
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
        return "redirect:/ajax/play";
    }
}