package at.berger.timesup.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;


@Component
@SessionScope
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionModel {
    private String gameId;
    private String playerName;
    private String teamName;
    private boolean admin;
}
