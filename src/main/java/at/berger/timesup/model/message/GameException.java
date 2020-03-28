package at.berger.timesup.model.message;

import lombok.Data;

@Data
public class GameException extends RuntimeException {
    private final GameMessage gameMessage;
    private final Severity severity;

    public GameException(Severity severity, GameMessage message)  {
        super(message.getMessage());
        this.gameMessage = message;
        this.severity = severity;
    }

    public GameException(GameMessage message)  {
        super(message.getMessage());
        this.gameMessage = message;
        this.severity = Severity.ERROR;
    }
}
