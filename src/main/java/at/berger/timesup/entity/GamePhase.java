package at.berger.timesup.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum GamePhase {
    ALL_WORDS("free style"),
    ONE_WORD("only one word"),
    MIME("mime");

    private final String displayName;
}
